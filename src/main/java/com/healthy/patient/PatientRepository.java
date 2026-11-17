package com.healthy.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByUsuarioId(Long usuarioId);

    boolean existsByCpfAndUsuarioIdNot(String cpf, Long usuarioId);

    boolean existsByCpf(String cpf);
}
