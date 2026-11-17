package com.healthy.doctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NewPrescriptionRequest(
        @JsonProperty("pacienteId") @NotNull Long patientId,
        @JsonProperty("medicamentos") @NotEmpty @Size(max = 30) List<@Valid MedicationRequest> medications,
        @JsonProperty("observacoes") @Size(max = 1000) String notes) {
}
