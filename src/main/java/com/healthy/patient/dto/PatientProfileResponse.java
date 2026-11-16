package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/** Ficha do paciente (só o próprio paciente a recebe). cpf, dataNascimento e telefone são null até ele preencher. */
public record PatientProfileResponse(
        @JsonProperty("nome") String name,
        String email,
        String cpf,
        @JsonProperty("dataNascimento") LocalDate birthDate,
        @JsonProperty("telefone") String phone) {
}
