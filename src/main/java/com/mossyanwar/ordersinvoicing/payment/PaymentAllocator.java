package com.mossyanwar.ordersinvoicing.payment;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.money.Money;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides how a payment is split across a customer's open invoices. Pure logic with no database
 * access; the per-invoice checks (open status, not paying more than is owed) are enforced again by
 * {@code Invoice.applyPayment} when the plan is carried out.
 */
public final class PaymentAllocator {

    public record OpenInvoice(long invoiceId, LocalDate dueDate, Money outstanding) {
    }

    public record Allocation(long invoiceId, Money amount) {
    }

    private PaymentAllocator() {
    }

    /**
     * Pays the invoice that fell due first, then the next, and so on. The last invoice touched may
     * be part paid. A payment larger than everything owed is refused, since unallocated customer
     * credit is not modelled.
     */
    public static List<Allocation> oldestFirst(Money payment, List<OpenInvoice> openInvoices) {
        requirePositive(payment);
        Money owed = openInvoices.stream().map(OpenInvoice::outstanding).reduce(Money.ZERO, Money::plus);
        if (payment.isGreaterThan(owed)) {
            throw new BusinessRuleException("Payment of %s is more than the %s owed across the customer's open invoices"
                    .formatted(payment, owed));
        }
        List<OpenInvoice> byDueDate = openInvoices.stream()
                .sorted(Comparator.comparing(OpenInvoice::dueDate).thenComparing(OpenInvoice::invoiceId))
                .toList();
        List<Allocation> allocations = new ArrayList<>();
        Money remaining = payment;
        for (OpenInvoice invoice : byDueDate) {
            if (remaining.isZero()) {
                break;
            }
            Money amount = remaining.min(invoice.outstanding());
            if (amount.isPositive()) {
                allocations.add(new Allocation(invoice.invoiceId(), amount));
                remaining = remaining.minus(amount);
            }
        }
        return allocations;
    }

    /** Checks allocations chosen by the client: positive, one per invoice, and adding up to the payment. */
    public static List<Allocation> explicit(Money payment, List<Allocation> requested) {
        requirePositive(payment);
        if (requested.isEmpty()) {
            throw new BusinessRuleException("At least one allocation is needed");
        }
        Set<Long> seen = new HashSet<>();
        Money total = Money.ZERO;
        for (Allocation allocation : requested) {
            if (!seen.add(allocation.invoiceId())) {
                throw new BusinessRuleException("Invoice " + allocation.invoiceId() + " appears more than once in the allocations");
            }
            if (!allocation.amount().isPositive()) {
                throw new BusinessRuleException("Allocation amounts must be positive");
            }
            total = total.plus(allocation.amount());
        }
        if (!total.equals(payment)) {
            throw new BusinessRuleException("Allocations add up to %s but the payment is %s".formatted(total, payment));
        }
        return List.copyOf(requested);
    }

    private static void requirePositive(Money payment) {
        if (!payment.isPositive()) {
            throw new BusinessRuleException("Payment amounts must be positive");
        }
    }
}
