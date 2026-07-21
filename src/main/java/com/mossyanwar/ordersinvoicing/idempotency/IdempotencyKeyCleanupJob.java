package com.mossyanwar.ordersinvoicing.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;

/** Keys only need to outlive a client's retry window; older ones are deleted every hour. */
@Component
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final IdempotencyService idempotency;
    private final Clock clock;
    private final Duration retention;

    public IdempotencyKeyCleanupJob(IdempotencyService idempotency, Clock clock,
                                    @Value("${app.idempotency.retention}") Duration retention) {
        this.idempotency = idempotency;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.idempotency.cleanup-cron}", zone = "${app.timezone}")
    @Transactional
    public void run() {
        int deleted = idempotency.deleteOlderThan(OffsetDateTime.now(clock).minus(retention));
        if (deleted > 0) {
            log.info("deleted {} expired idempotency key(s)", deleted);
        }
    }
}
