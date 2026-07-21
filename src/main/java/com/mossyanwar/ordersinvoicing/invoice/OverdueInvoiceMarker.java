package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.audit.AuditLog;
import com.mossyanwar.ordersinvoicing.audit.AuditedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;

/**
 * Moves open invoices past their due date to OVERDUE.
 *
 * <p>Each invoice is updated in its own short transaction. If a payment lands on an invoice at the
 * same moment, the optimistic lock fails for that one invoice only; it is skipped and looked at
 * again on the next run, and the rest of the batch still goes through.
 */
@Component
public class OverdueInvoiceMarker {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceMarker.class);

    private final InvoiceRepository invoices;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public OverdueInvoiceMarker(InvoiceRepository invoices, AuditLog audit, TransactionTemplate tx, Clock clock) {
        this.invoices = invoices;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    /** Returns how many invoices were marked overdue. */
    public int markOverdue(LocalDate today) {
        List<Long> candidates = invoices.findIdsDueBefore(today, EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PART_PAID));
        int marked = 0;
        for (Long id : candidates) {
            try {
                if (Boolean.TRUE.equals(tx.execute(status -> markOne(id, today)))) {
                    marked++;
                }
            } catch (OptimisticLockingFailureException e) {
                log.info("invoice {} changed while being marked overdue; it will be checked on the next run", id);
            }
        }
        return marked;
    }

    private boolean markOne(long invoiceId, LocalDate today) {
        return invoices.findById(invoiceId)
                .map(invoice -> {
                    InvoiceStatus was = invoice.getStatus();
                    boolean marked = invoice.markOverdueIfPastDue(today, clock.instant());
                    if (marked) {
                        audit.record(AuditedEntity.INVOICE, invoiceId, "MARKED_OVERDUE", was, invoice.getStatus(),
                                "due " + invoice.getDueDate());
                    }
                    return marked;
                })
                .orElse(false);
    }
}
