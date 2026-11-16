package com.healthy.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSafetyChecksTest {

    private static final String REAL_SECRET = "Zx9Qw7Lm3Vb5Nc1Hd8Ks2Jf6Tg4Yr0PoUiAeSdFgHjKlMn";

    /** Uma configuração de produção completa e segura; cada teste estraga uma coisa. */
    private static MockEnvironment safeEnvironment() {
        return new MockEnvironment()
                .withProperty("healthy.jwt.secret", REAL_SECRET)
                .withProperty("healthy.cors.allowed-origins", "https://app.exemplo.com")
                .withProperty("healthy.frontend-url", "https://app.exemplo.com")
                .withProperty("healthy.mail.from", "nao-responda@exemplo.com");
    }

    private static void verify(MockEnvironment environment) {
        new ProductionSafetyChecks(environment).verify();
    }

    @Test
    void acceptsASafeConfiguration() {
        assertThatCode(() -> verify(safeEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void refusesTheDevelopmentSecret() {
        MockEnvironment environment = safeEnvironment()
                .withProperty("healthy.jwt.secret", "dev-only-secret-troque-em-producao-0123456789abcdef");

        assertThatThrownBy(() -> verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("segredo JWT");
    }

    @Test
    void refusesSeedDataBecauseItCreatesKnownPasswords() {
        assertThatThrownBy(() -> verify(safeEnvironment().withProperty("healthy.seed", "true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("healthy.seed");
    }

    @Test
    void refusesTheH2Console() {
        assertThatThrownBy(() -> verify(safeEnvironment().withProperty("spring.h2.console.enabled", "true")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("H2");
    }

    @Test
    void refusesMissingRequiredVariables() {
        for (String property : new String[] {
                "healthy.jwt.secret", "healthy.cors.allowed-origins", "healthy.frontend-url", "healthy.mail.from"}) {
            MockEnvironment environment = safeEnvironment().withProperty(property, "");
            assertThatThrownBy(() -> verify(environment))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(property);
        }
    }

    @Test
    void refusesPlaceholdersThatWereNeverResolved() {
        // O que acontece quando a variável de ambiente não existe: o texto "${...}" fica literal
        MockEnvironment environment = safeEnvironment().withProperty("healthy.cors.allowed-origins", "${HEALTHY_CORS_ORIGINS}");

        assertThatThrownBy(() -> verify(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("healthy.cors.allowed-origins");
    }
}
