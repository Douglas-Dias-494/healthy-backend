package com.healthy.doctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Um medicamento da receita que o médico está emitindo (espelha Medicamento do Angular). */
public record MedicationRequest(
        @JsonProperty("nome") @NotBlank @Size(max = 100) String name,
        @JsonProperty("dosagem") @NotBlank @Size(max = 50) String dosage,
        @JsonProperty("quantidade") @NotNull @Min(1) @Max(1000) Integer quantity,
        @JsonProperty("instrucoes") @NotBlank @Size(max = 300) String instructions) {
}
