package com.healthy.doctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo de POST /api/medicos/me/atendimentos. O médico é o dono do token: não há campo "médico".
 * O atendimento já aconteceu, por isso a data não pode ser futura.
 */
public record NewAppointmentRequest(
        @JsonProperty("pacienteId") @NotNull Long patientId,
        @JsonProperty("data") @NotNull @PastOrPresent LocalDate date,
        @JsonProperty("local") @NotBlank @Size(max = 200) String location,
        @JsonProperty("motivo") @NotBlank @Size(max = 200) String reason) {
}
