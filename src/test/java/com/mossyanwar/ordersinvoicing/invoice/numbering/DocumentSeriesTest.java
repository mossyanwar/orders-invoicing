package com.mossyanwar.ordersinvoicing.invoice.numbering;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSeriesTest {

    @Test
    void formatsWithPrefixYearAndSixDigits() {
        assertThat(DocumentSeries.INVOICE.format(2026, 123)).isEqualTo("INV-2026-000123");
        assertThat(DocumentSeries.CREDIT_NOTE.format(2026, 1)).isEqualTo("CN-2026-000001");
    }

    @Test
    void keepsGoingPastAMillion() {
        assertThat(DocumentSeries.INVOICE.format(2026, 1_000_000)).isEqualTo("INV-2026-1000000");
    }
}
