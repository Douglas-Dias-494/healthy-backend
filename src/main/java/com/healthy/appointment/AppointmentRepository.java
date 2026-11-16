package com.healthy.appointment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Consultas de um paciente, da mais recente para a mais antiga.
     * O @EntityGraph traz o médico no MESMO select. Sem ele, cada consulta dispararia um select extra
     * para buscar o médico (o famoso problema "N+1").
     */
    @EntityGraph(attributePaths = "doctor")
    List<Appointment> findByPatientIdOrderByDateDescIdDesc(Long patientId);

    /** Consultas de um médico, da mais recente para a mais antiga, já com o paciente. */
    @EntityGraph(attributePaths = "patient")
    List<Appointment> findByDoctorIdOrderByDateDescIdDesc(Long doctorId);

    /** O médico já atendeu este paciente? (regra para poder emitir receita) */
    boolean existsByDoctorIdAndPatientId(Long doctorId, Long patientId);
}
