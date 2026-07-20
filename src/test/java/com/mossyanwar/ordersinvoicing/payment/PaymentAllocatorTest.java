package com.mossyanwar.ordersinvoicing.payment;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.payment.PaymentAllocator.Allocation;
import com.mossyanwar.ordersinvoicing.payment.PaymentAllocator.OpenInvoice;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentAllocatorTest {

    private static final LocalDate MARCH = LocalDate.of(2026, 3, 31);
    private static final LocalDate APRIL = LocalDate.of(2026, 4, 30);
    private static final LocalDate MAY = LocalDate.of(2026, 5, 31);

    @Nested
    class OldestFirst {

        private final List<OpenInvoice> open = List.of(
                new OpenInvoice(3, MAY, Money.of("30.00")),
                new OpenInvoice(1, MARCH, Money.of("100.00")),
                new OpenInvoice(2, APRIL, Money.of("50.00")));

        @Test
        void paysTheEarliestDueInvoiceFirstAndPartPaysTheLastOneTouched() {
            List<Allocation> allocations = PaymentAllocator.oldestFirst(Money.of("120.00"), open);

            assertThat(allocations).containsExactly(
                    new Allocation(1, Money.of("100.00")),
                    new Allocation(2, Money.of("20.00")));
        }

        @Test
        void canClearEverythingExactly() {
            List<Allocation> allocations = PaymentAllocator.oldestFirst(Money.of("180.00"), open);

            assertThat(allocations).extracting(Allocation::invoiceId).containsExactly(1L, 2L, 3L);
            assertThat(allocations).extracting(Allocation::amount)
                    .containsExactly(Money.of("100.00"), Money.of("50.00"), Money.of("30.00"));
        }

        @Test
        void refusesToTakeMoreThanIsOwed() {
            assertThatThrownBy(() -> PaymentAllocator.oldestFirst(Money.of("180.01"), open))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Payment of 180.01 is more than the 180.00 owed across the customer's open invoices");
        }

        @Test
        void refusesAPaymentWhenNothingIsOwed() {
            assertThatThrownBy(() -> PaymentAllocator.oldestFirst(Money.of("1.00"), List.of()))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void breaksTiesOnDueDateByInvoiceId() {
            List<OpenInvoice> sameDay = List.of(
                    new OpenInvoice(9, MARCH, Money.of("10.00")),
                    new OpenInvoice(4, MARCH, Money.of("10.00")));

            assertThat(PaymentAllocator.oldestFirst(Money.of("10.00"), sameDay))
                    .containsExactly(new Allocation(4, Money.of("10.00")));
        }

        @Test
        void refusesZero() {
            assertThatThrownBy(() -> PaymentAllocator.oldestFirst(Money.ZERO, open)).isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class Explicit {

        @Test
        void acceptsAllocationsThatAddUpToThePayment() {
            List<Allocation> requested = List.of(new Allocation(1, Money.of("60.00")), new Allocation(2, Money.of("40.00")));

            assertThat(PaymentAllocator.explicit(Money.of("100.00"), requested)).isEqualTo(requested);
        }

        @Test
        void rejectsAllocationsThatDoNotAddUp() {
            List<Allocation> requested = List.of(new Allocation(1, Money.of("60.00")));

            assertThatThrownBy(() -> PaymentAllocator.explicit(Money.of("100.00"), requested))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Allocations add up to 60.00 but the payment is 100.00");
        }

        @Test
        void rejectsTheSameInvoiceTwice() {
            List<Allocation> requested = List.of(new Allocation(1, Money.of("50.00")), new Allocation(1, Money.of("50.00")));

            assertThatThrownBy(() -> PaymentAllocator.explicit(Money.of("100.00"), requested))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("more than once");
        }

        @Test
        void rejectsNonPositiveAllocations() {
            List<Allocation> requested = List.of(new Allocation(1, Money.of("100.00")), new Allocation(2, Money.ZERO));

            assertThatThrownBy(() -> PaymentAllocator.explicit(Money.of("100.00"), requested))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }
}
