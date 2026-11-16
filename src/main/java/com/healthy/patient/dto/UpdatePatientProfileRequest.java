package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.br.CPF;

import java.time.LocalDate;

/** Corpo de PUT /api/pacientes/me/ficha. CPF e telefone chegam só com dígitos (o Angular tira a máscara). */
public record UpdatePatientProfileRequest(
        @JsonProperty("nome") @NotBlank @Size(min = 3, max = 100) String name,
        // @Pattern garante só dígitos; @CPF confere os dígitos verificadores (CPF inventado é recusado)
        @JsonProperty("cpf") @NotBlank @Pattern(regexp = "\\d{11}", message = "O CPF deve ter 11 dígitos.") @CPF String cpf,
        @JsonProperty("dataNascimento") @NotNull @Past LocalDate birthDate,
        @JsonProperty("telefone") @Pattern(regexp = "\\d{10,11}", message = "O telefone deve ter DDD e 10 ou 11 dígitos.") String phone) {
}
