package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.customer.Customer;
import com.mossyanwar.ordersinvoicing.order.Order;
import com.mossyanwar.ordersinvoicing.product.Product;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");
    private static final LocalDate ISSUED_ON = LocalDate.of(2026, 3, 2);

    private final Customer customer = new Customer("Acme Ltd", "accounts@acme.test", "1 High St, Leeds", 30, NOW);

    @Test
    void copiesLinesAndWorksOutTotalsAndDueDate() {
        Invoice invoice = issue(line("10.00", 3, VatRate.STANDARD), line("4.00", 2, VatRate.REDUCED), line("8.00", 1, VatRate.ZERO));

        assertThat(invoice.getLines()).hasSize(3);
        assertThat(invoice.getLines()).extracting(InvoiceLine::getLineNumber).containsExactly(1, 2, 3);
        assertThat(invoice.getNetTotal()).isEqualTo(Money.of("46.00"));
        assertThat(invoice.getVatTotal()).isEqualTo(Money.of("6.40"));
        assertThat(invoice.getGrossTotal()).isEqualTo(Money.of("52.40"));
        assertThat(invoice.getDueDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.outstanding()).isEqualTo(Money.of("52.40"));
    }

    @Test
    void breaksVatDownByRate() {
        Invoice invoice = issue(line("10.00", 1, VatRate.STANDARD), line("5.00", 1, VatRate.STANDARD), line("4.00", 1, VatRate.REDUCED));

        assertThat(invoice.vatBreakdown()).containsOnlyKeys(VatRate.STANDARD, VatRate.REDUCED);
        assertThat(invoice.vatBreakdown().get(VatRate.STANDARD).net()).isEqualTo(Money.of("15.00"));
        assertThat(invoice.vatBreakdown().get(VatRate.STANDARD).vat()).isEqualTo(Money.of("3.00"));
        assertThat(invoice.vatBreakdown().get(VatRate.REDUCED).vat()).isEqualTo(Money.of("0.20"));
    }

    @Test
    void partPaymentThenFullPayment() {
        Invoice invoice = issue(line("50.00", 2, VatRate.ZERO));

        invoice.applyPayment(Money.of("30.00"), NOW);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PART_PAID);
        assertThat(invoice.outstanding()).isEqualTo(Money.of("70.00"));

        invoice.applyPayment(Money.of("70.00"), NOW);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice.outstanding()).isEqualTo(Money.ZERO);
    }

    @Test
    void overpaymentIsRejectedAndNothingChanges() {
        Invoice invoice = issue(line("50.00", 1, VatRate.ZERO));

        assertThatThrownBy(() -> invoice.applyPayment(Money.of("50.01"), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Payment of 50.01 is more than the 50.00 outstanding on invoice INV-2026-000001");
        assertThat(invoice.getAmountPaid()).isEqualTo(Money.ZERO);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);
    }

    @Test
    void paidInvoicesTakeNoMorePayments() {
        Invoice invoice = issue(line("50.00", 1, VatRate.ZERO));
        invoice.applyPayment(Money.of("50.00"), NOW);

        assertThatThrownBy(() -> invoice.applyPayment(Money.of("0.01"), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("is PAID");
    }

    @Test
    void zeroAndNegativePaymentsAreRejected() {
        Invoice invoice = issue(line("50.00", 1, VatRate.ZERO));

        assertThatThrownBy(() -> invoice.applyPayment(Money.ZERO, NOW)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> invoice.applyPayment(Money.of("-1.00"), NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void becomesOverdueOnlyAfterTheDueDate() {
        Invoice invoice = issue(line("50.00", 1, VatRate.ZERO));

        assertThat(invoice.markOverdueIfPastDue(invoice.getDueDate(), NOW)).isFalse();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.ISSUED);

        assertThat(invoice.markOverdueIfPastDue(invoice.getDueDate().plusDays(1), NOW)).isTrue();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

        assertThat(invoice.markOverdueIfPastDue(invoice.getDueDate().plusDays(2), NOW)).isFalse();
    }

    @Test
    void aPartPaymentKeepsAnOverdueInvoiceOverdue() {
        Invoice invoice = issue(line("50.00", 1, VatRate.ZERO));
        invoice.markOverdueIfPastDue(invoice.getDueDate().plusDays(1), NOW);

        invoice.applyPayment(Money.of("10.00"), NOW);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.OVERDUE);

        invoice.applyPayment(Money.of("40.00"), NOW);
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void paidInvoicesNeverBecomeOverdue() {
        Invoice invoice = issue(line("50.00", 1, VatRate.ZERO));
        invoice.applyPayment(Money.of("50.00"), NOW);

        assertThat(invoice.markOverdueIfPastDue(invoice.getDueDate().plusDays(10), NOW)).isFalse();
        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void aZeroValueInvoiceIsPaidStraightAway() {
        Invoice invoice = issue(line("0.00", 1, VatRate.STANDARD));

        assertThat(invoice.getStatus()).isEqualTo(InvoiceStatus.PAID);
    }

    private record Line(String price, int quantity, VatRate rate) {
    }

    private static Line line(String price, int quantity, VatRate rate) {
        return new Line(price, quantity, rate);
    }

    private Invoice issue(Line... lines) {
        Order order = new Order(customer, NOW);
        int sku = 1;
        for (Line line : lines) {
            Product product = new Product("P-" + sku++, "Product", Money.of(line.price()), line.rate());
            order.addLine(product, line.quantity(), NOW);
        }
        order.confirm(NOW);
        order.fulfil(NOW);
        order.markInvoiced(NOW);
        return Invoice.issue(order, "INV-2026-000001", ISSUED_ON, NOW);
    }
}
