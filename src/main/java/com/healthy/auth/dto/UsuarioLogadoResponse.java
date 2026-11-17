package com.healthy.auth.dto;

import com.healthy.usuario.Perfil;

public record UsuarioLogadoResponse(Long id, String nome, Perfil perfil) {
}
