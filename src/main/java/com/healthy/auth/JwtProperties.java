package com.healthy.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "healthy.jwt")
public record JwtProperties(String secret, Duration expiracao) {
}
