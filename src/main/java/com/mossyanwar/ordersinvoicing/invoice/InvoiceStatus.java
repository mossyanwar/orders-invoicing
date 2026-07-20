package com.mossyanwar.ordersinvoicing.invoice;

/**
 * <pre>
 * ISSUED -> PART_PAID -> PAID
 *    |         |
 *    +---------+-> OVERDUE -> PAID   (a part payment keeps it OVERDUE)
 *    |
 *    +-> VOID   (fully credited before any payment)
 * </pre>
 */
public enum InvoiceStatus {

    ISSUED,
    PART_PAID,
    PAID,
    OVERDUE,
    VOID;

    /** Open invoices still have money owing and can take payments and credit notes. */
    public boolean isOpen() {
        return this == ISSUED || this == PART_PAID || this == OVERDUE;
    }
}
