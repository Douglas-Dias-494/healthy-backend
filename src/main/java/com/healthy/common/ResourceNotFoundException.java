package com.healthy.common;

/**
 * Recurso inexistente OU que pertence a outra pessoa. Os dois casos respondem 404 de propósito:
 * dizer "isso existe, mas não é seu" (403) revelaria que o id é válido.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
