package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.tracking.MedicationTracking;

import java.time.LocalDate;

public record TrackingResponse(
        Long id,
        @JsonProperty("receitaId") Long prescriptionId,
        @JsonProperty("medicamento") String medicationName,
        @JsonProperty("dosagem") String dosage,
        @JsonProperty("dataInicio") LocalDate startDate,
        @JsonProperty("quantidadeTotal") int totalQuantity,
        @JsonProperty("quantidadeRestante") int remainingQuantity) {

    public static TrackingResponse from(MedicationTracking tracking) {
        return new TrackingResponse(
                tracking.getId(),
                tracking.getPrescription().getId(),
                tracking.getMedicationName(),
                tracking.getDosage(),
                tracking.getStartDate(),
                tracking.getTotalQuantity(),
                tracking.getRemainingQuantity());
    }
}
