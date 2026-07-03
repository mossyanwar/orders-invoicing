package com.mossyanwar.ordersinvoicing.common.error;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " was not found");
    }
}
