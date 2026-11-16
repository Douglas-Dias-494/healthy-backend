package com.healthy.doctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Corpo de POST /api/medicos/me/receitas (espelha NovaReceitaRequest do Angular).
 * Não há campo de data nem de médico: a data é a de hoje e o médico é o dono do token.
 */
public record NewPrescriptionRequest(
        @JsonProperty("pacienteId") @NotNull Long patientId,
        // @Valid dentro do List faz o Spring validar também cada medicamento
        @JsonProperty("medicamentos") @NotEmpty @Size(max = 30) List<@Valid MedicationRequest> medications,
        @JsonProperty("observacoes") @Size(max = 1000) String notes) {
}
