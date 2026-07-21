package com.mossyanwar.ordersinvoicing.audit;

import com.mossyanwar.ordersinvoicing.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-events")
public class AuditController {

    private final AuditLog auditLog;

    public AuditController(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @GetMapping
    public PageResponse<AuditEventResponse> search(@RequestParam(required = false) AuditedEntity entityType,
                                                   @RequestParam(required = false) Long entityId,
                                                   @PageableDefault(size = 50, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(auditLog.search(entityType, entityId, pageable));
    }
}
