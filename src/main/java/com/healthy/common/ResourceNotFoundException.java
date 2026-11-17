package com.healthy.common;

/** Missing OR not owned by the caller. Both answer 404: a 403 would reveal that the id exists. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
