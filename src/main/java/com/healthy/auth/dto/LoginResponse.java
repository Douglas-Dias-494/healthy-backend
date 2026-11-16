package com.healthy.auth.dto;

import com.healthy.usuario.Perfil;

/** Resposta do login (espelha LoginResponse do Angular). */
public record LoginResponse(String token, Long id, String nome, Perfil perfil) {
}
