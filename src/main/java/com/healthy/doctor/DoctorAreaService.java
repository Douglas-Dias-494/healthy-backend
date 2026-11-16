package com.healthy.doctor;

import com.healthy.appointment.Appointment;
import com.healthy.appointment.AppointmentRepository;
import com.healthy.common.ForbiddenOperationException;
import com.healthy.doctor.dto.NewPrescriptionRequest;
import com.healthy.doctor.dto.PatientSeenResponse;
import com.healthy.prescription.PrescribedMedication;
import com.healthy.prescription.Prescription;
import com.healthy.prescription.PrescriptionRepository;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Regras da área do médico. O id do médico vem sempre do TOKEN, nunca do corpo da requisição. */
@Service
public class DoctorAreaService {

    private final AppointmentRepository appointments;
    private final PrescriptionRepository prescriptions;
    private final UsuarioRepository users;

    public DoctorAreaService(
            AppointmentRepository appointments, PrescriptionRepository prescriptions, UsuarioRepository users) {
        this.appointments = appointments;
        this.prescriptions = prescriptions;
        this.users = users;
    }

    /**
     * Pacientes do médico, um por paciente, com o atendimento mais recente.
     * As consultas chegam da mais nova para a mais antiga: ao guardar só a PRIMEIRA de cada paciente,
     * sobra a mais recente. LinkedHashMap preserva essa ordem.
     */
    @Transactional(readOnly = true)
    public List<PatientSeenResponse> listPatients(Long doctorId) {
        Map<Long, Appointment> latestByPatient = new LinkedHashMap<>();
        for (Appointment appointment : appointments.findByDoctorIdOrderByDateDescIdDesc(doctorId)) {
            latestByPatient.putIfAbsent(appointment.getPatient().getId(), appointment);
        }
        return latestByPatient.values().stream().map(PatientSeenResponse::from).toList();
    }

    @Transactional
    public void issuePrescription(Long doctorId, NewPrescriptionRequest request) {
        // REGRA DE NEGÓCIO: só emite receita para quem o médico já atendeu.
        if (!appointments.existsByDoctorIdAndPatientId(doctorId, request.patientId())) {
            throw new ForbiddenOperationException("Este paciente não está associado a você.");
        }

        Prescription prescription = new Prescription();
        prescription.setDoctor(users.getReferenceById(doctorId));
        prescription.setPatient(users.getReferenceById(request.patientId()));
        prescription.setDate(LocalDate.now()); // a data é do servidor: o cliente não escolhe
        prescription.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        request.medications().forEach(medication -> prescription.getItems().add(new PrescribedMedication(
                medication.name().trim(),
                medication.dosage().trim(),
                medication.quantity(),
                medication.instructions().trim())));

        prescriptions.save(prescription);
    }
}
