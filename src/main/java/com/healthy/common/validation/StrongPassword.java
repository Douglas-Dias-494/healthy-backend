package com.healthy.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de senha: 8 a 72 caracteres, ao menos uma letra e um número, e fora da lista de senhas comuns.
 * (72 é o limite de bytes do BCrypt. Passar disso faria o final da senha ser ignorado.)
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "A senha deve ter de 8 a 72 caracteres, com ao menos uma letra e um número, e não pode ser uma senha comum.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
