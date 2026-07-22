package com.mossyanwar.ordersinvoicing.invoice.document;

import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.invoice.InvoiceResponse;
import com.mossyanwar.ordersinvoicing.invoice.InvoiceStatus;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders an invoice as an A4 PDF. It shows the details HMRC lists for a full VAT invoice: a unique
 * sequential number, issue date (used as the tax point), the seller's name, address and VAT number,
 * the customer's name and address, and per line the description, quantity, unit price excluding VAT,
 * VAT rate and amounts, followed by totals and the VAT for each rate.
 */
@Component
@EnableConfigurationProperties(SellerDetails.class)
public class InvoicePdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final Color RULE = new Color(200, 200, 200);
    private static final Color SHADE = new Color(240, 240, 240);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9);
    private static final Font BODY_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Color.DARK_GRAY);

    private final SellerDetails seller;

    public InvoicePdfRenderer(SellerDetails seller) {
        this.seller = seller;
    }

    public byte[] render(InvoiceResponse invoice) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter.getInstance(document, out);
        document.addTitle("Invoice " + invoice.invoiceNumber());
        document.addAuthor(seller.name());
        document.open();
        try {
            document.add(header(invoice));
            document.add(parties(invoice));
            document.add(lines(invoice));
            document.add(summary(invoice));
            if (!invoice.creditNotes().isEmpty()) {
                document.add(creditNotes(invoice));
            }
            document.add(footer(invoice));
        } finally {
            document.close();
        }
        return out.toByteArray();
    }

    private PdfPTable header(InvoiceResponse invoice) {
        PdfPTable table = table(new float[]{3, 2});
        PdfPCell title = cell(new Phrase("VAT INVOICE", TITLE), Element.ALIGN_LEFT);
        table.addCell(title);

        PdfPTable facts = table(new float[]{1, 1.3f});
        fact(facts, "Invoice number", invoice.invoiceNumber());
        fact(facts, "Issue date / tax point", date(invoice.issueDate()));
        fact(facts, "Payment terms", invoice.paymentTermsDays() + " days");
        fact(facts, "Due date", date(invoice.dueDate()));
        if (invoice.status() != InvoiceStatus.ISSUED) {
            fact(facts, "Status", invoice.status().name().replace('_', ' '));
        }
        PdfPCell factsCell = new PdfPCell(facts);
        factsCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(factsCell);
        table.setSpacingAfter(16);
        return table;
    }

    private PdfPTable parties(InvoiceResponse invoice) {
        PdfPTable table = table(new float[]{1, 1});
        table.addCell(block("From", seller.name() + "\n" + seller.address()
                + "\nVAT registration number: " + seller.vatNumber() + "\n" + seller.email()));
        table.addCell(block("Bill to", invoice.customerName() + "\n" + invoice.billingAddress()));
        table.setSpacingAfter(16);
        return table;
    }

    private PdfPTable lines(InvoiceResponse invoice) {
        PdfPTable table = new PdfPTable(new float[]{0.4f, 3.2f, 0.6f, 1.1f, 0.7f, 1.1f, 1f, 1.1f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String heading : new String[]{"#", "Description", "Qty", "Unit price", "Rate", "Net", "VAT", "Total"}) {
            PdfPCell cell = new PdfPCell(new Phrase(heading, BODY_BOLD));
            cell.setBackgroundColor(SHADE);
            cell.setBorderColor(RULE);
            cell.setPadding(4);
            cell.setHorizontalAlignment("#".equals(heading) || "Description".equals(heading) ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
            table.addCell(cell);
        }
        for (InvoiceResponse.Line line : invoice.lines()) {
            table.addCell(lineCell(String.valueOf(line.lineNumber()), Element.ALIGN_LEFT));
            table.addCell(lineCell(line.description() + " (" + line.sku() + ")", Element.ALIGN_LEFT));
            table.addCell(lineCell(String.valueOf(line.quantity()), Element.ALIGN_RIGHT));
            table.addCell(lineCell(money(line.unitPrice()), Element.ALIGN_RIGHT));
            table.addCell(lineCell(rate(line.vatRate()), Element.ALIGN_RIGHT));
            table.addCell(lineCell(money(line.net()), Element.ALIGN_RIGHT));
            table.addCell(lineCell(money(line.vat()), Element.ALIGN_RIGHT));
            table.addCell(lineCell(money(line.gross()), Element.ALIGN_RIGHT));
        }
        table.setSpacingAfter(12);
        return table;
    }

    private PdfPTable summary(InvoiceResponse invoice) {
        PdfPTable vat = table(new float[]{1, 1.2f, 1.2f});
        vat.addCell(cell(new Phrase("VAT rate", BODY_BOLD), Element.ALIGN_LEFT));
        vat.addCell(cell(new Phrase("Net", BODY_BOLD), Element.ALIGN_RIGHT));
        vat.addCell(cell(new Phrase("VAT", BODY_BOLD), Element.ALIGN_RIGHT));
        for (InvoiceResponse.VatSummary line : invoice.vatBreakdown()) {
            vat.addCell(cell(new Phrase(rate(line.rate()), BODY), Element.ALIGN_LEFT));
            vat.addCell(cell(new Phrase(money(line.net()), BODY), Element.ALIGN_RIGHT));
            vat.addCell(cell(new Phrase(money(line.vat()), BODY), Element.ALIGN_RIGHT));
        }

        PdfPTable totals = table(new float[]{1.4f, 1});
        total(totals, "Total excluding VAT", invoice.netTotal(), false);
        total(totals, "VAT", invoice.vatTotal(), false);
        total(totals, "Invoice total", invoice.grossTotal(), true);
        if (invoice.amountPaid().isPositive()) {
            total(totals, "Paid", invoice.amountPaid(), false);
        }
        if (invoice.amountCredited().isPositive()) {
            total(totals, "Credited", invoice.amountCredited(), false);
        }
        total(totals, "Balance due", invoice.outstanding(), true);

        PdfPTable layout = table(new float[]{1, 0.2f, 1});
        PdfPCell left = new PdfPCell(vat);
        left.setBorder(Rectangle.NO_BORDER);
        layout.addCell(left);
        layout.addCell(cell(new Phrase(""), Element.ALIGN_LEFT));
        PdfPCell right = new PdfPCell(totals);
        right.setBorder(Rectangle.NO_BORDER);
        layout.addCell(right);
        layout.setSpacingAfter(12);
        return layout;
    }

    private PdfPTable creditNotes(InvoiceResponse invoice) {
        PdfPTable table = table(new float[]{1.5f, 1.5f, 1});
        PdfPCell heading = cell(new Phrase("Credit notes against this invoice", HEADING), Element.ALIGN_LEFT);
        heading.setColspan(3);
        table.addCell(heading);
        for (InvoiceResponse.CreditNoteSummary note : invoice.creditNotes()) {
            table.addCell(cell(new Phrase(note.creditNoteNumber(), BODY), Element.ALIGN_LEFT));
            table.addCell(cell(new Phrase(date(note.issueDate()), BODY), Element.ALIGN_LEFT));
            table.addCell(cell(new Phrase(money(note.grossTotal()), BODY), Element.ALIGN_RIGHT));
        }
        table.setSpacingAfter(12);
        return table;
    }

    private Paragraph footer(InvoiceResponse invoice) {
        Paragraph footer = new Paragraph(
                "Amounts are in pounds sterling. VAT is calculated on each line and rounded to the nearest penny. "
                        + "Please quote " + invoice.invoiceNumber() + " with your payment.", SMALL);
        footer.setSpacingBefore(8);
        return footer;
    }

    private static PdfPTable table(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        return table;
    }

    private static PdfPCell cell(Phrase phrase, int alignment) {
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(2);
        return cell;
    }

    private static PdfPCell lineCell(String text, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, BODY));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(RULE);
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(4);
        return cell;
    }

    private static PdfPCell block(String heading, String text) {
        Paragraph content = new Paragraph();
        content.add(new Phrase(heading + "\n", HEADING));
        content.add(new Phrase(text, BODY));
        PdfPCell cell = new PdfPCell();
        cell.addElement(content);
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static void fact(PdfPTable table, String label, String value) {
        table.addCell(cell(new Phrase(label, BODY), Element.ALIGN_LEFT));
        table.addCell(cell(new Phrase(value, BODY_BOLD), Element.ALIGN_RIGHT));
    }

    private static void total(PdfPTable table, String label, Money amount, boolean bold) {
        Font font = bold ? BODY_BOLD : BODY;
        table.addCell(cell(new Phrase(label, font), Element.ALIGN_LEFT));
        table.addCell(cell(new Phrase(money(amount), font), Element.ALIGN_RIGHT));
    }

    static String money(Money amount) {
        return String.format(Locale.UK, "£%,.2f", amount.amount());
    }

    private static String rate(VatRate rate) {
        return rate.percent().stripTrailingZeros().toPlainString() + "%";
    }

    private static String date(LocalDate date) {
        return DATE.format(date);
    }
}
