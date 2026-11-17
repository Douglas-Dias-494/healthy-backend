package com.healthy.appointment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Fetches the doctor in the same query (avoids the N+1 problem).
    @EntityGraph(attributePaths = "doctor")
    List<Appointment> findByPatientIdOrderByDateDescIdDesc(Long patientId);

    @EntityGraph(attributePaths = "patient")
    List<Appointment> findByDoctorIdOrderByDateDescIdDesc(Long doctorId);

    boolean existsByDoctorIdAndPatientId(Long doctorId, Long patientId);

    boolean existsByDoctorIdAndPatientIdAndDate(Long doctorId, Long patientId, java.time.LocalDate date);
}
