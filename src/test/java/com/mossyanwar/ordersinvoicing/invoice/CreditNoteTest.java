package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.customer.Customer;
import com.mossyanwar.ordersinvoicing.order.Order;
import com.mossyanwar.ordersinvoicing.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditNoteTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 2);

    private final AtomicInteger numbersTaken = new AtomicInteger();
    private final Supplier<String> numbers = () -> "CN-2026-%06d".formatted(numbersTaken.incrementAndGet());

    @Test
    void crediting_everything_on_an_unpaid_invoice_voids_it() {
        Invoice invoice = invoice(line("10.00", 3, VatRate.STANDARD), line("8.00", 1, VatRate.ZERO));

        CreditNote note = invoice.credit(Map.of(), "order raised in error", TODAY, NOW, numbers);

        assertThat(note.getCreditNoteNumber()).isEqualTo("CN-2026-000001");
        assertThat(note.getGrossTotal()).isEqualTo(invoice.getGrossTotal());
        assertThat(note.getVatTotal()).isEqualTo(invoice.getVatTotal());
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VOID);
        assertThat(invoice.outstanding()).isEqualTo(Money.ZERO);
        assertThat(invoice.getCreditNotes()).containsExactly(note);
    }

    @Test
    void a_partial_credit_reduces_the_balance_and_leaves_the_invoice_open() {
        Invoice invoice = invoice(line("10.00", 3, VatRate.STANDARD));   // 30.00 + 6.00

        CreditNote note = invoice.credit(Map.of(1L, 1), "one damaged", TODAY, NOW, numbers);

        assertThat(note.getNetTotal()).isEqualTo(Money.of("10.00"));
        assertThat(note.getVatTotal()).isEqualTo(Money.of("2.00"));
        assertThat(invoice.getAmountCredited()).isEqualTo(Money.of("12.00"));
        assertThat(invoice.outstanding()).isEqualTo(Money.of("24.00"));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.getLines().getFirst().remainingQuantity()).isEqualTo(2);
    }

    @Test
    void vat_on_piecemeal_credits_adds_up_to_exactly_the_line_vat() {
        // 5 x 0.10 at 5%: line VAT is 0.025 -> 0.03. Rounding each one-unit credit on its own
        // (0.005 -> 0.01) would refund 0.05 of VAT.
        Invoice invoice = invoice(line("0.10", 5, VatRate.REDUCED));
        assertThat(invoice.getVatTotal()).isEqualTo(Money.of("0.03"));

        Money vatCredited = Money.ZERO;
        for (int i = 0; i < 5; i++) {
            vatCredited = vatCredited.plus(invoice.credit(Map.of(1L, 1), "returned", TODAY, NOW, numbers).getVatTotal());
        }

        assertThat(vatCredited).isEqualTo(Money.of("0.03"));
        assertThat(invoice.getAmountCredited()).isEqualTo(invoice.getGrossTotal());
        assertThat(invoice.outstanding()).isEqualTo(Money.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.VOID);
    }

    @Test
    void cannot_credit_more_units_than_are_left() {
        Invoice invoice = invoice(line("10.00", 2, VatRate.STANDARD));
        invoice.credit(Map.of(1L, 1), "first", TODAY, NOW, numbers);

        assertThatThrownBy(() -> invoice.credit(Map.of(1L, 2), "too many", TODAY, NOW, numbers))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Line 1 has 1 left to credit, so 2 cannot be credited");
        assertThat(numbersTaken).hasValue(1);
    }

    @Test
    void cannot_credit_money_that_has_already_been_paid() {
        Invoice invoice = invoice(line("10.00", 2, VatRate.ZERO));
        invoice.applyPayment(Money.of("15.00"), NOW);

        assertThatThrownBy(() -> invoice.credit(Map.of(), "cancel it all", TODAY, NOW, numbers))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("refunds of money already paid are not supported");
        assertThat(invoice.getAmountCredited()).isEqualTo(Money.ZERO);
        assertThat(invoice.getLines().getFirst().remainingQuantity()).isEqualTo(2);
        assertThat(numbersTaken).hasValue(0);
    }

    @Test
    void paying_part_and_crediting_the_rest_settles_the_invoice() {
        Invoice invoice = invoice(line("10.00", 1, VatRate.ZERO), line("5.00", 1, VatRate.ZERO));
        invoice.applyPayment(Money.of("10.00"), NOW);

        invoice.credit(Map.of(2L, 1), "not delivered", TODAY, NOW, numbers);

        assertThat(invoice.outstanding()).isEqualTo(Money.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void void_and_paid_invoices_cannot_be_credited() {
        Invoice invoice = invoice(line("10.00", 1, VatRate.ZERO));
        invoice.credit(Map.of(), "cancel", TODAY, NOW, numbers);

        assertThatThrownBy(() -> invoice.credit(Map.of(), "again", TODAY, NOW, numbers))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is VOID");
    }

    @Test
    void unknown_lines_are_reported() {
        Invoice invoice = invoice(line("10.00", 1, VatRate.ZERO));

        assertThatThrownBy(() -> invoice.credit(Map.of(99L, 1), "wrong line", TODAY, NOW, numbers))
                .isInstanceOf(NotFoundException.class);
        assertThat(numbersTaken).hasValue(0);
    }

    private record Line(String price, int quantity, VatRate rate) {
    }

    private static Line line(String price, int quantity, VatRate rate) {
        return new Line(price, quantity, rate);
    }

    /** Builds an issued invoice and gives its lines ids 1, 2, 3... as if they had been saved. */
    private static Invoice invoice(Line... lines) {
        Customer customer = new Customer("Acme Ltd", "accounts@acme.test", "1 High St, Leeds", 30, NOW);
        Order order = new Order(customer, NOW);
        int sku = 1;
        for (Line line : lines) {
            order.addLine(new Product("P-" + sku++, "Product", Money.of(line.price()), line.rate()), line.quantity(), NOW);
        }
        order.confirm(NOW);
        order.fulfil(NOW);
        order.markInvoiced(NOW);
        Invoice invoice = Invoice.issue(order, "INV-2026-000001", TODAY, NOW);
        long id = 1;
        for (InvoiceLine line : invoice.getLines()) {
            ReflectionTestUtils.setField(line, "id", id++);
        }
        return invoice;
    }
}
