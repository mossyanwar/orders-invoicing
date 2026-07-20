package com.mossyanwar.ordersinvoicing.admin;

import com.mossyanwar.ordersinvoicing.invoice.OverdueInvoiceMarker;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;

/** Lets an admin run the nightly overdue check now, for today's date only. */
@RestController
@RequestMapping("/api/admin/jobs")
public class AdminJobController {

    private final OverdueInvoiceMarker marker;
    private final Clock clock;

    public AdminJobController(OverdueInvoiceMarker marker, Clock clock) {
        this.marker = marker;
        this.clock = clock;
    }

    public record JobResult(LocalDate asOf, int invoicesMarkedOverdue) {
    }

    @PostMapping("/mark-overdue")
    public JobResult markOverdue() {
        LocalDate today = LocalDate.now(clock);
        return new JobResult(today, marker.markOverdue(today));
    }
}
