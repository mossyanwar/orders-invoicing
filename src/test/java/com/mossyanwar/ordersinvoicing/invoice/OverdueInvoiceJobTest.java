package com.mossyanwar.ordersinvoicing.invoice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OverdueInvoiceJobTest {

    @Mock
    OverdueInvoiceMarker marker;

    @Test
    void usesTheUkDateNotTheUtcDate() {
        // 23:30 UTC on 30 June is already 1 July in London (BST)
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T23:30:00Z"), ZoneId.of("Europe/London"));

        new OverdueInvoiceJob(marker, clock).run();

        verify(marker).markOverdue(LocalDate.of(2026, 7, 1));
    }
}
