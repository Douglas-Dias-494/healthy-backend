package com.healthy.patient;

import com.healthy.appointment.AppointmentRepository;
import com.healthy.common.BusinessRuleException;
import com.healthy.common.ConflictException;
import com.healthy.common.ResourceNotFoundException;
import com.healthy.patient.dto.AppointmentResponse;
import com.healthy.patient.dto.PrescriptionResponse;
import com.healthy.patient.dto.StartTrackingRequest;
import com.healthy.patient.dto.TrackingResponse;
import com.healthy.prescription.PrescribedMedication;
import com.healthy.prescription.Prescription;
import com.healthy.prescription.PrescriptionRepository;
import com.healthy.tracking.MedicationTracking;
import com.healthy.tracking.MedicationTrackingRepository;
import com.healthy.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Regras da área do paciente. Todo método recebe o id do paciente que veio do TOKEN (nunca da URL ou do
 * corpo) e filtra os dados por ele: assim cada paciente só enxerga e altera o que é seu.
 */
@Service
public class PatientAreaService {

    private final AppointmentRepository appointments;
    private final PrescriptionRepository prescriptions;
    private final MedicationTrackingRepository trackings;
    private final UsuarioRepository users;

    public PatientAreaService(
            AppointmentRepository appointments,
            PrescriptionRepository prescriptions,
            MedicationTrackingRepository trackings,
            UsuarioRepository users) {
        this.appointments = appointments;
        this.prescriptions = prescriptions;
        this.trackings = trackings;
        this.users = users;
    }

    // readOnly = true: avisa o banco que é só leitura (mais leve) e mantém a sessão aberta para ler
    // dados "lazy" (médico, medicamentos) ao montar a resposta.
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAppointments(Long patientId) {
        return appointments.findByPatientIdOrderByDateDescIdDesc(patientId).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PrescriptionResponse> listPrescriptions(Long patientId) {
        return prescriptions.findByPatientIdOrderByDateDescIdDesc(patientId).stream()
                .map(PrescriptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrackingResponse> listTrackings(Long patientId) {
        return trackings.findByPatientIdOrderByIdAsc(patientId).stream()
                .map(TrackingResponse::from)
                .toList();
    }

    @Transactional
    public TrackingResponse startTracking(Long patientId, StartTrackingRequest request) {
        // 1) A receita precisa ser DESTE paciente. Receita de outra pessoa responde "não encontrada".
        Prescription prescription = prescriptions
                .findByIdAndPatientId(request.prescriptionId(), patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Receita não encontrada."));

        // 2) O remédio precisa constar nessa receita
        PrescribedMedication medication = prescription.getItems().stream()
                .filter(item -> item.getName().equals(request.medicationName()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Este medicamento não consta na receita."));

        // 3) Não pode acompanhar duas vezes o mesmo remédio da mesma receita
        if (trackings.existsByPatientIdAndPrescriptionIdAndMedicationName(
                patientId, prescription.getId(), medication.getName())) {
            throw new ConflictException("Este remédio já está em acompanhamento.");
        }

        MedicationTracking tracking = new MedicationTracking();
        // getReferenceById: usa o paciente só como "chave estrangeira", sem buscá-lo no banco
        tracking.setPatient(users.getReferenceById(patientId));
        tracking.setPrescription(prescription);
        tracking.setMedicationName(medication.getName());
        tracking.setDosage(medication.getDosage()); // vem da receita, não do cliente
        tracking.setStartDate(request.startDate());
        tracking.setTotalQuantity(request.quantity());
        tracking.setRemainingQuantity(request.quantity());

        try {
            return TrackingResponse.from(trackings.saveAndFlush(tracking));
        } catch (DataIntegrityViolationException e) {
            // Dois pedidos simultâneos passam pela checagem acima; a restrição UNIQUE do banco é a garantia final.
            throw new ConflictException("Este remédio já está em acompanhamento.");
        }
    }

    @Transactional
    public TrackingResponse registerDose(Long patientId, Long trackingId) {
        MedicationTracking tracking = findOwnedTracking(patientId, trackingId);
        tracking.takeDose(); // ao fechar a transação o Hibernate grava a mudança sozinho (dirty checking)
        return TrackingResponse.from(tracking);
    }

    @Transactional
    public void endTracking(Long patientId, Long trackingId) {
        trackings.delete(findOwnedTracking(patientId, trackingId));
    }

    private MedicationTracking findOwnedTracking(Long patientId, Long trackingId) {
        return trackings.findByIdAndPatientId(trackingId, patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Acompanhamento não encontrado."));
    }
}
