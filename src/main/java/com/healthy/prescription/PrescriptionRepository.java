package com.healthy.prescription;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    @EntityGraph(attributePaths = {"doctor", "items"})
    List<Prescription> findByPatientIdOrderByDateDescIdDesc(Long patientId);

    @EntityGraph(attributePaths = "items")
    Optional<Prescription> findByIdAndPatientId(Long id, Long patientId);
}
