package com.healthy.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Lê as propriedades "healthy.jwt.*" do application.properties.
 *
 * @param secret    chave usada para assinar os tokens (mínimo 32 caracteres)
 * @param expiracao quanto tempo um token vale, por exemplo 8h
 */
@ConfigurationProperties(prefix = "healthy.jwt")
public record JwtProperties(String secret, Duration expiracao) {
}
