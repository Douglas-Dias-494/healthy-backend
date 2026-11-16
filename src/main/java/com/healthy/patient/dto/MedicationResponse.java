package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.prescription.PrescribedMedication;

/** Espelha Medicamento do Angular. */
public record MedicationResponse(
        @JsonProperty("nome") String name,
        @JsonProperty("dosagem") String dosage,
        @JsonProperty("quantidade") int quantity,
        @JsonProperty("instrucoes") String instructions) {

    public static MedicationResponse from(PrescribedMedication medication) {
        return new MedicationResponse(
                medication.getName(), medication.getDosage(), medication.getQuantity(), medication.getInstructions());
    }
}
