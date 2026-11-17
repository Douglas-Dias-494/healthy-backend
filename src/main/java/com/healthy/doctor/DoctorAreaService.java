package com.healthy.doctor;

import com.healthy.appointment.Appointment;
import com.healthy.appointment.AppointmentRepository;
import com.healthy.common.ConflictException;
import com.healthy.common.Emails;
import com.healthy.common.ForbiddenOperationException;
import com.healthy.common.ResourceNotFoundException;
import com.healthy.doctor.dto.NewAppointmentRequest;
import com.healthy.doctor.dto.NewPrescriptionRequest;
import com.healthy.doctor.dto.PatientLookupResponse;
import com.healthy.doctor.dto.PatientSeenResponse;
import com.healthy.prescription.PrescribedMedication;
import com.healthy.prescription.Prescription;
import com.healthy.prescription.PrescriptionRepository;
import com.healthy.security.AttemptGuard;
import com.healthy.usuario.Perfil;
import com.healthy.usuario.Usuario;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DoctorAreaService {

    private static final int LOOKUPS_PER_MINUTE = 30;

    private final AppointmentRepository appointments;
    private final PrescriptionRepository prescriptions;
    private final UsuarioRepository users;
    private final AttemptGuard attempts;

    public DoctorAreaService(
            AppointmentRepository appointments,
            PrescriptionRepository prescriptions,
            UsuarioRepository users,
            AttemptGuard attempts) {
        this.appointments = appointments;
        this.prescriptions = prescriptions;
        this.users = users;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public PatientLookupResponse findPatientByEmail(Long doctorId, String rawEmail, String ip) {
        // There is no patient listing (it would expose health data): exact e-mail only, id and name only, rate limited.
        String key = "patient-lookup:" + doctorId;
        attempts.check("patient-lookup", key, LOOKUPS_PER_MINUTE, Duration.ofMinutes(1), ip);
        attempts.record(key);

        return users.findByEmail(Emails.normalize(rawEmail))
                .filter(user -> user.getPerfil() == Perfil.PACIENTE)
                .map(PatientLookupResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum paciente encontrado com este e-mail."));
    }

    @Transactional
    public void registerAppointment(Long doctorId, NewAppointmentRequest request) {
        Usuario patient = users.findById(request.patientId())
                .filter(user -> user.getPerfil() == Perfil.PACIENTE)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado."));

        if (appointments.existsByDoctorIdAndPatientIdAndDate(doctorId, patient.getId(), request.date())) {
            throw new ConflictException("Já existe um atendimento deste paciente nesta data.");
        }

        Appointment appointment = new Appointment();
        appointment.setDoctor(users.getReferenceById(doctorId));
        appointment.setPatient(patient);
        appointment.setDate(request.date());
        appointment.setLocation(request.location().trim());
        appointment.setReason(request.reason().trim());

        try {
            appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe um atendimento deste paciente nesta data.");
        }
    }

    @Transactional(readOnly = true)
    public List<PatientSeenResponse> listPatients(Long doctorId) {
        // Appointments arrive newest first: keeping only the first one per patient leaves the latest.
        Map<Long, Appointment> latestByPatient = new LinkedHashMap<>();
        for (Appointment appointment : appointments.findByDoctorIdOrderByDateDescIdDesc(doctorId)) {
            latestByPatient.putIfAbsent(appointment.getPatient().getId(), appointment);
        }
        return latestByPatient.values().stream().map(PatientSeenResponse::from).toList();
    }

    @Transactional
    public void issuePrescription(Long doctorId, NewPrescriptionRequest request) {
        // Business rule: prescriptions only for patients this doctor has already attended.
        if (!appointments.existsByDoctorIdAndPatientId(doctorId, request.patientId())) {
            throw new ForbiddenOperationException("Este paciente não está associado a você.");
        }

        Prescription prescription = new Prescription();
        prescription.setDoctor(users.getReferenceById(doctorId));
        prescription.setPatient(users.getReferenceById(request.patientId()));
        prescription.setDate(LocalDate.now());
        prescription.setNotes(request.notes() == null || request.notes().isBlank() ? null : request.notes().trim());
        request.medications().forEach(medication -> prescription.getItems().add(new PrescribedMedication(
                medication.name().trim(),
                medication.dosage().trim(),
                medication.quantity(),
                medication.instructions().trim())));

        prescriptions.save(prescription);
    }
}
