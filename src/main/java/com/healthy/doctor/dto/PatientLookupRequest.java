package com.healthy.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/medicos/me/pacientes/busca. Vai no corpo (e não na URL) para o e-mail não ficar em logs. */
public record PatientLookupRequest(@NotBlank @Size(max = 100) String email) {
}
