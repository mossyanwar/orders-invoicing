package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.error.ConflictException;
import com.mossyanwar.ordersinvoicing.common.error.InvalidStateTransitionException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.customer.Customer;
import com.mossyanwar.ordersinvoicing.product.Product;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-03-02T09:00:00Z");

    private final Customer customer = new Customer("Acme Ltd", "accounts@acme.test", "1 High St, Leeds", 30, NOW);
    private final Product widget = new Product("W-1", "Widget", Money.of("10.00"), VatRate.STANDARD);
    private final Product book = new Product("B-1", "Book", Money.of("8.00"), VatRate.ZERO);

    @Test
    void newOrderIsDraft() {
        assertThat(new Order(customer, NOW).getStatus()).isEqualTo(OrderStatus.DRAFT);
    }

    @Test
    void linesCopyThePriceAndVatRateFromTheCatalogue() {
        Order order = new Order(customer, NOW);
        order.addLine(widget, 3, NOW);

        widget.update("Widget", Money.of("12.00"), VatRate.REDUCED, true);

        OrderLine line = order.getLines().getFirst();
        assertThat(line.getUnitPrice()).isEqualTo(Money.of("10.00"));
        assertThat(line.getVatRate()).isEqualTo(VatRate.STANDARD);
    }

    @Test
    void totalsAddUpTheLines() {
        Order order = new Order(customer, NOW);
        order.addLine(widget, 3, NOW);   // 30.00 + 6.00
        order.addLine(book, 2, NOW);     // 16.00 + 0.00

        assertThat(order.totals().net()).isEqualTo(Money.of("46.00"));
        assertThat(order.totals().vat()).isEqualTo(Money.of("6.00"));
        assertThat(order.totals().gross()).isEqualTo(Money.of("52.00"));
    }

    @Test
    void cannotConfirmAnEmptyOrder() {
        Order order = new Order(customer, NOW);

        assertThatThrownBy(() -> order.confirm(NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("at least one line");
    }

    @Test
    void followsTheHappyPath() {
        Order order = new Order(customer, NOW);
        order.addLine(widget, 1, NOW);

        order.confirm(NOW);
        order.fulfil(NOW);
        order.markInvoiced(NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVOICED);
    }

    @Test
    void cannotSkipFulfilment() {
        Order order = confirmedOrder();

        assertThatThrownBy(() -> order.markInvoiced(NOW))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("CONFIRMED to INVOICED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void cannotCancelOnceFulfilled() {
        Order order = confirmedOrder();
        order.fulfil(NOW);

        assertThatThrownBy(() -> order.cancel(NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelledOrdersStayCancelled() {
        Order order = confirmedOrder();
        order.cancel(NOW);

        assertThatThrownBy(() -> order.confirm(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> order.fulfil(NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void linesAreLockedAfterConfirmation() {
        Order order = confirmedOrder();

        assertThatThrownBy(() -> order.addLine(book, 1, NOW))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("only be changed while it is DRAFT");
    }

    @Test
    void inactiveProductsCannotBeOrdered() {
        widget.update("Widget", Money.of("10.00"), VatRate.STANDARD, false);
        Order order = new Order(customer, NOW);

        assertThatThrownBy(() -> order.addLine(widget, 1, NOW)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void changesMoveUpdatedAt() {
        Order order = new Order(customer, NOW);
        Instant later = NOW.plusSeconds(60);

        order.addLine(widget, 1, later);

        assertThat(order.getUpdatedAt()).isEqualTo(later);
    }

    private Order confirmedOrder() {
        Order order = new Order(customer, NOW);
        order.addLine(widget, 1, NOW);
        order.confirm(NOW);
        return order;
    }
}
