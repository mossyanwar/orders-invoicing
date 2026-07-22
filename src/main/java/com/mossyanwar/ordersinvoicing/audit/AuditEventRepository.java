package com.mossyanwar.ordersinvoicing.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByEntityType(AuditedEntity entityType, Pageable pageable);

    Page<AuditEvent> findByEntityTypeAndEntityId(AuditedEntity entityType, Long entityId, Pageable pageable);
}
