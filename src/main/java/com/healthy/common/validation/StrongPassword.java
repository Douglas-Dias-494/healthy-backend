package com.healthy.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Password policy: 8-72 characters (72 is BCrypt's byte limit), a letter and a digit, not a common password. */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "A senha deve ter de 8 a 72 caracteres, com ao menos uma letra e um número, e não pode ser uma senha comum.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
