package com.healthy.doctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientLookupRequest(@NotBlank @Size(max = 100) String email) {
}
