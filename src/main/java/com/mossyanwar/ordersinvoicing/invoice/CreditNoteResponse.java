package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;

import java.time.LocalDate;
import java.util.List;

public record CreditNoteResponse(
        Long id,
        String creditNoteNumber,
        Long invoiceId,
        String invoiceNumber,
        LocalDate issueDate,
        String reason,
        List<Line> lines,
        Money netTotal,
        Money vatTotal,
        Money grossTotal,
        InvoiceStatus invoiceStatus,
        Money invoiceOutstanding) {

    public record Line(Long invoiceLineId, String sku, String description, int quantity, Money unitPrice,
                       VatRate vatRate, Money net, Money vat, Money gross) {

        static Line from(CreditNoteLine line) {
            return new Line(line.getInvoiceLineId(), line.getSku(), line.getDescription(), line.getQuantity(),
                    line.getUnitPrice(), line.getVatRate(), line.getNetAmount(), line.getVatAmount(),
                    line.getGrossAmount());
        }
    }

    static CreditNoteResponse from(CreditNote note) {
        Invoice invoice = note.getInvoice();
        return new CreditNoteResponse(note.getId(), note.getCreditNoteNumber(), invoice.getId(),
                invoice.getInvoiceNumber(), note.getIssueDate(), note.getReason(),
                note.getLines().stream().map(Line::from).toList(),
                note.getNetTotal(), note.getVatTotal(), note.getGrossTotal(),
                invoice.getStatus(), invoice.outstanding());
    }
}
