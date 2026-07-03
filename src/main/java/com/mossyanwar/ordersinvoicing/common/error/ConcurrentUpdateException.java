package com.mossyanwar.ordersinvoicing.common.error;

/** The client sent a version that is no longer current. */
public class ConcurrentUpdateException extends ConflictException {

    public ConcurrentUpdateException(String resource, Object id, long expectedVersion, long actualVersion) {
        super("%s %s is at version %d but the request was based on version %d"
                .formatted(resource, id, actualVersion, expectedVersion));
    }
}
