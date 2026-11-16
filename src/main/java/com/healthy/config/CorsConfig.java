package com.healthy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Libera o site do Angular para chamar a API. O navegador bloqueia chamadas entre origens diferentes,
 * a menos que o servidor autorize (CORS). As origens vêm de healthy.cors.allowed-origins:
 * em desenvolvimento http://localhost:4200; em produção, o endereço real do site.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        List<String> origins = properties.allowedOrigins() == null ? List.of() : properties.allowedOrigins();
        if (origins.stream().anyMatch(origin -> origin.contains("*"))) {
            throw new IllegalStateException("healthy.cors.allowed-origins não pode usar curinga (*). Liste os sites.");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Authorization é o cabeçalho que leva o token JWT
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // O navegador só pode ler "Retry-After" (limite de tentativas) se for exposto aqui
        config.setExposedHeaders(List.of("Retry-After"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
