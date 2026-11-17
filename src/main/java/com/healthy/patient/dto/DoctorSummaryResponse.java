package com.healthy.patient.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.usuario.Usuario;

public record DoctorSummaryResponse(
        Long id,
        @JsonProperty("nome") String name,
        @JsonProperty("especialidade") String specialty,
        String crm) {

    public static DoctorSummaryResponse from(Usuario doctor) {
        return new DoctorSummaryResponse(doctor.getId(), doctor.getNome(), doctor.getEspecialidade(), doctor.getCrm());
    }
}
