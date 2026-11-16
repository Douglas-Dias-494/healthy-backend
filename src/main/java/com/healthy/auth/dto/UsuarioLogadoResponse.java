package com.healthy.auth.dto;

import com.healthy.usuario.Perfil;

/** Resposta de GET /api/auth/me: quem é o dono do token enviado. */
public record UsuarioLogadoResponse(Long id, String nome, Perfil perfil) {
}
