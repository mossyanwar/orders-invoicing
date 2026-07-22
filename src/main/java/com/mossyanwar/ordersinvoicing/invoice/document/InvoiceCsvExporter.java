package com.mossyanwar.ordersinvoicing.invoice.document;

import com.mossyanwar.ordersinvoicing.common.csv.CsvWriter;
import com.mossyanwar.ordersinvoicing.invoice.Invoice;
import com.mossyanwar.ordersinvoicing.invoice.InvoiceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.Writer;
import java.time.LocalDate;
import java.util.List;

/**
 * Writes invoices issued in a date range as CSV, one page of rows at a time so a large export does
 * not load every invoice into memory at once.
 */
@Component
public class InvoiceCsvExporter {

    static final List<String> HEADER = List.of("invoice_number", "issue_date", "due_date", "customer_id",
            "customer_name", "status", "net_total", "vat_total", "gross_total", "amount_paid", "amount_credited",
            "outstanding");

    private static final int PAGE_SIZE = 500;

    private final InvoiceRepository invoices;

    public InvoiceCsvExporter(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    public int write(Writer out, LocalDate from, LocalDate to) {
        CsvWriter csv = new CsvWriter(out);
        csv.row(HEADER);
        int rows = 0;
        Page<Invoice> page;
        int pageNumber = 0;
        do {
            page = invoices.findByIssueDateBetween(from, to, PageRequest.of(pageNumber++, PAGE_SIZE, Sort.by("id")));
            for (Invoice invoice : page) {
                csv.row(List.of(invoice.getInvoiceNumber(), invoice.getIssueDate(), invoice.getDueDate(),
                        invoice.getCustomerId(), invoice.getCustomerName(), invoice.getStatus(),
                        invoice.getNetTotal(), invoice.getVatTotal(), invoice.getGrossTotal(),
                        invoice.getAmountPaid(), invoice.getAmountCredited(), invoice.outstanding()));
                rows++;
            }
        } while (page.hasNext());
        return rows;
    }
}
