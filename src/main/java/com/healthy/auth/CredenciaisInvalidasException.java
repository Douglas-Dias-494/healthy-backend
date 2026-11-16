package com.healthy.auth;

/** Login recusado. A mensagem é sempre a mesma, de propósito (ver AuthService.login). */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha incorretos.");
    }
}
