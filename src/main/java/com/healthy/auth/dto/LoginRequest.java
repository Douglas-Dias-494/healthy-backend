package com.healthy.auth.dto;

import com.healthy.usuario.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotNull Perfil perfil,
        @NotBlank @Size(max = 100) String email,
        @NotBlank @Size(max = 72) String senha) {
}
