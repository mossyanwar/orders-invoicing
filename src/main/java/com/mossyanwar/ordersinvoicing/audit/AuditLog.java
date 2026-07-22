package com.mossyanwar.ordersinvoicing.audit;

import com.mossyanwar.ordersinvoicing.security.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Writes audit events. {@link Propagation#MANDATORY} means an event can only be written inside the
 * transaction that makes the change, so the two commit or roll back together.
 */
@Service
public class AuditLog {

    private static final int MAX_DETAIL = 1000;

    private final AuditEventRepository events;
    private final Clock clock;

    public AuditLog(AuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditedEntity type, long entityId, String action, String detail) {
        record(type, entityId, action, null, null, detail);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditedEntity type, long entityId, String action, Enum<?> from, Enum<?> to, String detail) {
        events.save(new AuditEvent(clock.instant(), CurrentUser.name(), type, entityId, action,
                from == null ? null : from.name(), to == null ? null : to.name(), truncate(detail)));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> search(AuditedEntity type, Long entityId, Pageable pageable) {
        Page<AuditEvent> page;
        if (type == null) {
            page = events.findAll(pageable);
        } else if (entityId == null) {
            page = events.findByEntityType(type, pageable);
        } else {
            page = events.findByEntityTypeAndEntityId(type, entityId, pageable);
        }
        return page.map(AuditEventResponse::from);
    }

    private static String truncate(String detail) {
        return detail == null || detail.length() <= MAX_DETAIL ? detail : detail.substring(0, MAX_DETAIL);
    }
}
