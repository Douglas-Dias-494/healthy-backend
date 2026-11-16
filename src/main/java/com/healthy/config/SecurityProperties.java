package com.healthy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** healthy.security.*: ajustes de segurança. */
@ConfigurationProperties(prefix = "healthy.security")
public record SecurityProperties(Duration resetTokenTtl) {
}
