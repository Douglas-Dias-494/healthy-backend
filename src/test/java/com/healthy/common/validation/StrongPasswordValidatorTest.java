package com.healthy.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    private boolean valid(String password) {
        return validator.isValid(password, null);
    }

    @Test
    void acceptsLongPasswordsWithLettersAndDigits() {
        assertThat(valid("Segredo2026")).isTrue();
        assertThat(valid("abcdefg1")).isTrue(); // exatamente 8
        assertThat(valid("Café-com-leite-123")).isTrue(); // acentos e símbolos
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(valid("Abc1234")).isFalse(); // 7
    }

    @Test
    void rejectsPasswordsWithoutADigitOrWithoutALetter() {
        assertThat(valid("somenteletras")).isFalse();
        assertThat(valid("9876543210")).isFalse();
    }

    @Test
    void rejectsCommonPasswordsRegardlessOfCase() {
        assertThat(valid("12345678a")).isFalse();
        assertThat(valid("Password1")).isFalse();
        assertThat(valid("SENHA123")).isFalse();
    }

    @Test
    void rejectsPasswordsOverBcryptLimit() {
        assertThat(valid("a1" + "x".repeat(70))).isTrue(); // 72 bytes
        assertThat(valid("a1" + "x".repeat(71))).isFalse(); // 73 bytes: o BCrypt ignoraria o final
    }

    @Test
    void leavesNullToTheNotBlankConstraint() {
        assertThat(valid(null)).isTrue();
    }
}
