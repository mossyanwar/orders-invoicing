package com.mossyanwar.ordersinvoicing.audit;

import java.time.Instant;

public record AuditEventResponse(
        Long id,
        Instant occurredAt,
        String actor,
        AuditedEntity entityType,
        Long entityId,
        String action,
        String fromStatus,
        String toStatus,
        String detail) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getOccurredAt(), event.getActor(), event.getEntityType(),
                event.getEntityId(), event.getAction(), event.getFromStatus(), event.getToStatus(), event.getDetail());
    }
}
