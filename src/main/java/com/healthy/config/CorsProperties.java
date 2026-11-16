package com.healthy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** healthy.cors.allowed-origins: sites autorizados a chamar a API pelo navegador. */
@ConfigurationProperties(prefix = "healthy.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
