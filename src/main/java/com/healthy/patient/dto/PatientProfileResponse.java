package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

public record PatientProfileResponse(
        @JsonProperty("nome") String name,
        String email,
        String cpf,
        @JsonProperty("dataNascimento") LocalDate birthDate,
        @JsonProperty("telefone") String phone) {
}
