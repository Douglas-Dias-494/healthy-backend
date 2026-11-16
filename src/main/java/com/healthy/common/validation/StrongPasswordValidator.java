package com.healthy.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_BYTES = 72;

    // Senhas que atacantes testam primeiro. (Em letras minúsculas: a comparação ignora maiúsculas.)
    private static final Set<String> COMMON_PASSWORDS = Set.of(
            "12345678", "123456789", "1234567890", "12345678a", "123qweasd", "1q2w3e4r", "1qaz2wsx", "q1w2e3r4",
            "abc12345", "abcd1234", "a1234567", "password", "password1", "password123", "qwerty123", "qwerty1234",
            "admin123", "admin1234", "welcome1", "welcome123", "letmein123", "iloveyou1", "senha123", "senha1234",
            "senha12345", "brasil123", "mudar123", "trocar123", "teste123", "teste1234");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) {
            return true; // ausência é problema do @NotBlank, não desta regra
        }
        if (password.length() < MIN_LENGTH || password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return false;
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        return hasLetter && hasDigit && !COMMON_PASSWORDS.contains(password.toLowerCase(Locale.ROOT));
    }
}
