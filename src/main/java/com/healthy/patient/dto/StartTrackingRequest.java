package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo de POST /api/pacientes/me/acompanhamentos (espelha AtivarAcompanhamentoRequest do Angular).
 *
 * O Angular também envia "dosagem", mas o servidor IGNORA: a dosagem é lida da própria receita.
 * Nunca confie em dados que o cliente poderia forjar quando o servidor já os conhece.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StartTrackingRequest(
        @JsonProperty("receitaId") @NotNull Long prescriptionId,
        @JsonProperty("medicamento") @NotBlank @Size(max = 100) String medicationName,
        @JsonProperty("dataInicio") @NotNull LocalDate startDate,
        @JsonProperty("quantidade") @NotNull @Min(1) @Max(1000) Integer quantity) {
}
