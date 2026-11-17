package com.healthy.doctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.usuario.Usuario;

public record PatientLookupResponse(Long id, @JsonProperty("nome") String name) {

    public static PatientLookupResponse from(Usuario patient) {
        return new PatientLookupResponse(patient.getId(), patient.getNome());
    }
}
