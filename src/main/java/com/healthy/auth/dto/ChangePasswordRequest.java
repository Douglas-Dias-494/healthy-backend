package com.healthy.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.healthy.common.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @JsonProperty("senhaAtual") @NotBlank @Size(max = 72) String currentPassword,
        @JsonProperty("novaSenha") @NotBlank @StrongPassword String newPassword) {
}
