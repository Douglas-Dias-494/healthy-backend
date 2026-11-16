package com.healthy.common;

/** O usuário está logado, mas não pode fazer esta operação (regra de negócio). Responde 403. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
