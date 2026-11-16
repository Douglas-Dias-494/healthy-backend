package com.healthy.tracking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MedicationTrackingRepository extends JpaRepository<MedicationTracking, Long> {

    List<MedicationTracking> findByPatientIdOrderByIdAsc(Long patientId);

    /** Sempre filtra pelo dono: o id sozinho nunca basta. */
    Optional<MedicationTracking> findByIdAndPatientId(Long id, Long patientId);

    boolean existsByPatientIdAndPrescriptionIdAndMedicationName(Long patientId, Long prescriptionId, String medicationName);
}
