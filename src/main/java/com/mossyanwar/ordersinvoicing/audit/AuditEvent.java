package com.mossyanwar.ordersinvoicing.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

@Entity
@Immutable
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditedEntity entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private String action;

    private String fromStatus;

    private String toStatus;

    private String detail;

    protected AuditEvent() {
    }

    AuditEvent(Instant occurredAt, String actor, AuditedEntity entityType, Long entityId, String action,
               String fromStatus, String toStatus, String detail) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.detail = detail;
    }

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public AuditedEntity getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getAction() {
        return action;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public String getDetail() {
        return detail;
    }
}
