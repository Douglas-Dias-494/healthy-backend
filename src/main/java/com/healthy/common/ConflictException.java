package com.healthy.common;

/** O pedido conflita com o estado atual (por exemplo, algo que já existe). Responde 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
