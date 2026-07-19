package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        Long orderId,
        Long customerId,
        String customerName,
        String billingAddress,
        InvoiceStatus status,
        LocalDate issueDate,
        LocalDate dueDate,
        int paymentTermsDays,
        List<Line> lines,
        List<VatSummary> vatBreakdown,
        Money netTotal,
        Money vatTotal,
        Money grossTotal,
        Money amountPaid,
        Money amountCredited,
        Money outstanding,
        List<CreditNoteSummary> creditNotes,
        long version) {

    public record Line(
            Long id,
            int lineNumber,
            String sku,
            String description,
            int quantity,
            Money unitPrice,
            VatRate vatRate,
            Money net,
            Money vat,
            Money gross,
            int creditedQuantity) {

        static Line from(InvoiceLine line) {
            return new Line(line.getId(), line.getLineNumber(), line.getSku(), line.getDescription(),
                    line.getQuantity(), line.getUnitPrice(), line.getVatRate(),
                    line.getNetAmount(), line.getVatAmount(), line.getGrossAmount(), line.getCreditedQuantity());
        }
    }

    public record CreditNoteSummary(Long id, String creditNoteNumber, LocalDate issueDate, Money grossTotal) {

        static CreditNoteSummary from(CreditNote note) {
            return new CreditNoteSummary(note.getId(), note.getCreditNoteNumber(), note.getIssueDate(), note.getGrossTotal());
        }
    }

    public record VatSummary(VatRate rate, Money net, Money vat) {
    }

    public static InvoiceResponse from(Invoice invoice) {
        List<VatSummary> breakdown = invoice.vatBreakdown().entrySet().stream()
                .map((Map.Entry<VatRate, LineAmounts> e) -> new VatSummary(e.getKey(), e.getValue().net(), e.getValue().vat()))
                .toList();
        return new InvoiceResponse(invoice.getId(), invoice.getInvoiceNumber(), invoice.getOrderId(),
                invoice.getCustomerId(), invoice.getCustomerName(), invoice.getBillingAddress(), invoice.getStatus(),
                invoice.getIssueDate(), invoice.getDueDate(), invoice.getPaymentTermsDays(),
                invoice.getLines().stream().map(Line::from).toList(), breakdown,
                invoice.getNetTotal(), invoice.getVatTotal(), invoice.getGrossTotal(),
                invoice.getAmountPaid(), invoice.getAmountCredited(), invoice.outstanding(),
                invoice.getCreditNotes().stream().map(CreditNoteSummary::from).toList(), invoice.getVersion());
    }
}
