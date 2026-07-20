package com.mossyanwar.ordersinvoicing.invoice.numbering;

public enum DocumentSeries {

    INVOICE("INV"),
    CREDIT_NOTE("CN");

    private final String prefix;

    DocumentSeries(String prefix) {
        this.prefix = prefix;
    }

    /** For example {@code INV-2026-000123}. */
    public String format(int year, long value) {
        return "%s-%d-%06d".formatted(prefix, year, value);
    }
}
