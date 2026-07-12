package com.mossyanwar.ordersinvoicing.common.error;

/** The request clashes with the current state of a resource. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
