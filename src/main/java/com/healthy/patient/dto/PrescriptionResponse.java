package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.prescription.Prescription;

import java.time.LocalDate;
import java.util.List;

/** Espelha Receita do Angular. */
public record PrescriptionResponse(
        Long id,
        @JsonProperty("medico") DoctorSummaryResponse doctor,
        @JsonProperty("data") LocalDate date,
        @JsonProperty("medicamentos") List<MedicationResponse> medications,
        @JsonProperty("observacoes") String notes) {

    public static PrescriptionResponse from(Prescription prescription) {
        return new PrescriptionResponse(
                prescription.getId(),
                DoctorSummaryResponse.from(prescription.getDoctor()),
                prescription.getDate(),
                prescription.getItems().stream().map(MedicationResponse::from).toList(),
                prescription.getNotes() == null ? "" : prescription.getNotes());
    }
}
