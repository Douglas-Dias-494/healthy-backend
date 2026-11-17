package com.healthy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "healthy.security")
public record SecurityProperties(Duration resetTokenTtl) {
}
