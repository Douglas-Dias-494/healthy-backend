package com.healthy.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/** Production refuses to start with missing or unsafe configuration: failing at boot beats running exposed. */
@Component
@Profile("prod")
public class ProductionSafetyChecks {

    private static final List<String> REQUIRED = List.of(
            "healthy.jwt.secret",
            "healthy.cors.allowed-origins",
            "healthy.frontend-url",
            "healthy.mail.from");

    private final Environment environment;

    public ProductionSafetyChecks(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void verify() {
        for (String property : REQUIRED) {
            if (isMissing(property)) {
                throw new IllegalStateException(
                        "Configuração obrigatória ausente em produção: " + property
                                + ". Defina a variável de ambiente correspondente (ver application-prod.properties).");
            }
        }
        if (environment.getProperty("healthy.jwt.secret", "").contains("dev-only")) {
            throw new IllegalStateException("Produção não pode usar o segredo JWT de desenvolvimento. Defina HEALTHY_JWT_SECRET.");
        }
        if (environment.getProperty("healthy.seed", Boolean.class, false)) {
            throw new IllegalStateException("healthy.seed=true criaria usuários com senha conhecida. Desligue em produção.");
        }
        if (environment.getProperty("spring.h2.console.enabled", Boolean.class, false)) {
            throw new IllegalStateException("O console do H2 dá acesso total ao banco. Desligue em produção.");
        }
    }

    /** Spring may leave an unresolved "${VAR}" as literal text, which would otherwise pass as a real value. */
    private boolean isMissing(String property) {
        try {
            String value = environment.getProperty(property);
            return value == null || value.isBlank() || value.contains("${");
        } catch (IllegalArgumentException unresolvablePlaceholder) {
            return true;
        }
    }
}
