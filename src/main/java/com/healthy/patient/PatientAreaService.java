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
 * Patient-area rules. Every method receives the patient id taken from the JWT (never from the URL or body)
 * and filters by it, so a patient can only read and change their own data.
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
        // Someone else's prescription looks like a missing one (404), so ids cannot be probed.
        Prescription prescription = prescriptions
                .findByIdAndPatientId(request.prescriptionId(), patientId)
                .orElseThrow(() -> new ResourceNotFoundException("Receita não encontrada."));

        PrescribedMedication medication = prescription.getItems().stream()
                .filter(item -> item.getName().equals(request.medicationName()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Este medicamento não consta na receita."));

        if (trackings.existsByPatientIdAndPrescriptionIdAndMedicationName(
                patientId, prescription.getId(), medication.getName())) {
            throw new ConflictException("Este remédio já está em acompanhamento.");
        }

        MedicationTracking tracking = new MedicationTracking();
        tracking.setPatient(users.getReferenceById(patientId));
        tracking.setPrescription(prescription);
        tracking.setMedicationName(medication.getName());
        // Taken from the prescription, never from the client.
        tracking.setDosage(medication.getDosage());
        tracking.setStartDate(request.startDate());
        tracking.setTotalQuantity(request.quantity());
        tracking.setRemainingQuantity(request.quantity());

        try {
            return TrackingResponse.from(trackings.saveAndFlush(tracking));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Este remédio já está em acompanhamento.");
        }
    }

    @Transactional
    public TrackingResponse registerDose(Long patientId, Long trackingId) {
        MedicationTracking tracking = findOwnedTracking(patientId, trackingId);
        tracking.takeDose();
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
