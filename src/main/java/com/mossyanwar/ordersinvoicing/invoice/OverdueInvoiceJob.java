package com.mossyanwar.ordersinvoicing.invoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Runs the overdue check once a night, UK time. With more than one instance of the app this would
 * run on each of them; that is harmless here because marking is idempotent, but a lock such as
 * ShedLock would stop the duplicate work.
 */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class OverdueInvoiceJob {

    private static final Logger log = LoggerFactory.getLogger(OverdueInvoiceJob.class);

    private final OverdueInvoiceMarker marker;
    private final Clock clock;

    public OverdueInvoiceJob(OverdueInvoiceMarker marker, Clock clock) {
        this.marker = marker;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.invoices.overdue-cron}", zone = "${app.timezone}")
    public void run() {
        LocalDate today = LocalDate.now(clock);
        int marked = marker.markOverdue(today);
        log.info("overdue check for {} marked {} invoice(s) overdue", today, marked);
    }
}
