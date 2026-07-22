package com.mossyanwar.ordersinvoicing.invoice.document;

import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.invoice.InvoiceResponse;
import com.mossyanwar.ordersinvoicing.invoice.InvoiceStatus;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer(
            new SellerDetails("Example Trading Ltd", "Unit 4, Example Park, Leeds LS1 4AB", "GB000000000", "accounts@example.test"));

    @Test
    void producesAPdfWithTheInvoiceDetails() throws IOException {
        byte[] pdf = renderer.render(invoice(InvoiceStatus.ISSUED, List.of()));

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("VAT INVOICE", "INV-2026-000123", "2 March 2026", "1 April 2026");
        assertThat(text).contains("Example Trading Ltd", "VAT registration number: GB000000000");
        assertThat(text).contains("Acme Ltd", "1 High Street, Leeds LS1 1AA");
        assertThat(text).contains("Widget (W-1)", "Book (B-1)");
        assertThat(text).contains("£1,250.00", "£250.00", "£1,516.00");
        assertThat(text).contains("20%", "0%");
        assertThat(text).contains("Balance due");
    }

    @Test
    void showsPaymentsCreditsAndTheStatusOnceTheInvoiceHasMoved() throws IOException {
        InvoiceResponse invoice = invoice(InvoiceStatus.PART_PAID,
                List.of(new InvoiceResponse.CreditNoteSummary(9L, "CN-2026-000004", LocalDate.of(2026, 3, 10), Money.of("16.00"))));

        String text = text(renderer.render(invoice));

        assertThat(text).contains("Status", "PART PAID", "Paid", "£500.00", "Credited", "CN-2026-000004");
    }

    @Test
    void formatsMoneyInPoundsWithThousandsSeparators() {
        assertThat(InvoicePdfRenderer.money(Money.of("1234567.8"))).isEqualTo("£1,234,567.80");
        assertThat(InvoicePdfRenderer.money(Money.ZERO)).isEqualTo("£0.00");
    }

    private static String text(byte[] pdf) throws IOException {
        try (PdfReader reader = new PdfReader(pdf)) {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder text = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text.append(extractor.getTextFromPage(page)).append('\n');
            }
            return text.toString();
        }
    }

    private static InvoiceResponse invoice(InvoiceStatus status, List<InvoiceResponse.CreditNoteSummary> creditNotes) {
        List<InvoiceResponse.Line> lines = List.of(
                new InvoiceResponse.Line(1L, 1, "W-1", "Widget", 50, Money.of("25.00"), VatRate.STANDARD,
                        Money.of("1250.00"), Money.of("250.00"), Money.of("1500.00"), 0),
                new InvoiceResponse.Line(2L, 2, "B-1", "Book", 2, Money.of("8.00"), VatRate.ZERO,
                        Money.of("16.00"), Money.ZERO, Money.of("16.00"), creditNotes.isEmpty() ? 0 : 2));
        Money credited = creditNotes.stream().map(InvoiceResponse.CreditNoteSummary::grossTotal).reduce(Money.ZERO, Money::plus);
        Money paid = status == InvoiceStatus.PART_PAID ? Money.of("500.00") : Money.ZERO;
        return new InvoiceResponse(1L, "INV-2026-000123", 10L, 20L, "Acme Ltd", "1 High Street, Leeds LS1 1AA", status,
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 4, 1), 30, lines,
                List.of(new InvoiceResponse.VatSummary(VatRate.STANDARD, Money.of("1250.00"), Money.of("250.00")),
                        new InvoiceResponse.VatSummary(VatRate.ZERO, Money.of("16.00"), Money.ZERO)),
                Money.of("1266.00"), Money.of("250.00"), Money.of("1516.00"), paid, credited,
                Money.of("1516.00").minus(paid).minus(credited), creditNotes, 0);
    }
}
