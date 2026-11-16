package com.healthy.common;

/** Dados bem formados, mas que violam uma regra de negócio. Responde 400. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
