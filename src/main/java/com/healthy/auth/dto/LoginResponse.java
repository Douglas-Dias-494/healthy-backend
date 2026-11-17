package com.healthy.auth.dto;

import com.healthy.usuario.Perfil;

public record LoginResponse(String token, Long id, String nome, Perfil perfil) {
}
