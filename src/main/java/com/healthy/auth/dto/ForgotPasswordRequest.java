package com.healthy.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/auth/esqueci-senha. */
public record ForgotPasswordRequest(@NotBlank @Size(max = 100) String email) {
}
