package com.mossyanwar.ordersinvoicing.common.error;

public class InvalidStateTransitionException extends ConflictException {

    private final String currentStatus;
    private final String requestedStatus;

    public InvalidStateTransitionException(String resource, Object id, Enum<?> current, Enum<?> requested) {
        super("%s %s cannot move from %s to %s".formatted(resource, id, current, requested));
        this.currentStatus = current.name();
        this.requestedStatus = requested.name();
    }

    public String currentStatus() {
        return currentStatus;
    }

    public String requestedStatus() {
        return requestedStatus;
    }
}
