package com.healthy.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuditLoggerTest {

    @Test
    void masksEverythingButTheFirstLetterAndDomain() {
        assertThat(SecurityAuditLogger.maskEmail("maria@exemplo.com")).isEqualTo("m***@exemplo.com");
    }

    @Test
    void masksMalformedValuesCompletely() {
        assertThat(SecurityAuditLogger.maskEmail("sem-arroba")).isEqualTo("***");
        assertThat(SecurityAuditLogger.maskEmail("@x.com")).isEqualTo("***");
        assertThat(SecurityAuditLogger.maskEmail(null)).isEqualTo("***");
    }
}
