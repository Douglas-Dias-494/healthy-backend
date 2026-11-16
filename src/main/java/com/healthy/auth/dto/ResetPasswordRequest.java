package com.healthy.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de POST /api/auth/redefinir-senha: o token que veio no link do e-mail e a nova senha. */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @JsonProperty("novaSenha") @NotBlank @StrongPassword String newPassword) {
}
