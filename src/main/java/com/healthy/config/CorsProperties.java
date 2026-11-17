package com.healthy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "healthy.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
