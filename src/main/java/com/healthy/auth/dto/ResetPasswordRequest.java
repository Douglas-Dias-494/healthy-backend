package com.healthy.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank @Size(max = 200) String token,
        @JsonProperty("novaSenha") @NotBlank @StrongPassword String newPassword) {
}
