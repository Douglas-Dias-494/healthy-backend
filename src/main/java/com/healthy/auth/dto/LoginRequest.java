package com.healthy.auth.dto;

import com.healthy.usuario.Perfil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corpo de POST /api/auth/login (espelha LoginRequest do Angular).
 * Record = classe imutável só de dados. As anotações são validadas por @Valid no controller.
 */
public record LoginRequest(
        @NotNull Perfil perfil,
        @NotBlank @Size(max = 100) String email,
        // 72 é o limite de bytes que o BCrypt considera
        @NotBlank @Size(max = 72) String senha) {
}
