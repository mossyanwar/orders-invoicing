package com.mossyanwar.ordersinvoicing.invoice.numbering;

import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// years in the past so these counters never collide with invoices other tests issue today
@IntegrationTest
class DocumentNumberAllocatorIT {

    @Autowired
    DocumentNumberAllocator allocator;

    @Autowired
    TransactionTemplate tx;

    @Test
    void countsSeparatelyPerSeriesAndYear() {
        assertThat(allocate(DocumentSeries.INVOICE, 1990)).isEqualTo("INV-1990-000001");
        assertThat(allocate(DocumentSeries.INVOICE, 1990)).isEqualTo("INV-1990-000002");
        assertThat(allocate(DocumentSeries.CREDIT_NOTE, 1990)).isEqualTo("CN-1990-000001");
        assertThat(allocate(DocumentSeries.INVOICE, 1991)).isEqualTo("INV-1991-000001");
    }

    @Test
    void aNumberFromARolledBackTransactionIsHandedOutAgain() {
        assertThat(allocate(DocumentSeries.INVOICE, 1995)).isEqualTo("INV-1995-000001");

        String[] seenInsideRolledBackTransaction = new String[1];
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            seenInsideRolledBackTransaction[0] = allocator.next(DocumentSeries.INVOICE, 1995);
            throw new IllegalStateException("invoice could not be saved");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(seenInsideRolledBackTransaction[0]).isEqualTo("INV-1995-000002");
        assertThat(allocate(DocumentSeries.INVOICE, 1995)).isEqualTo("INV-1995-000002");
    }

    @Test
    void refusesToRunOutsideATransaction() {
        assertThatThrownBy(() -> allocator.next(DocumentSeries.INVOICE, 1996))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private String allocate(DocumentSeries series, int year) {
        return tx.execute(status -> allocator.next(series, year));
    }
}
