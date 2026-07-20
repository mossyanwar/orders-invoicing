package com.mossyanwar.ordersinvoicing.payment;

import com.mossyanwar.ordersinvoicing.common.money.Money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PaymentResponse(
        Long id,
        Long customerId,
        Money amount,
        LocalDate receivedOn,
        PaymentMethod method,
        String reference,
        List<Allocation> allocations,
        Instant createdAt) {

    public record Allocation(Long invoiceId, String invoiceNumber, Money amount) {
    }

    static PaymentResponse from(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getCustomerId(), payment.getAmount(),
                payment.getReceivedOn(), payment.getMethod(), payment.getReference(),
                payment.getAllocations().stream()
                        .map(a -> new Allocation(a.getInvoiceId(), a.getInvoiceNumber(), a.getAmount()))
                        .toList(),
                payment.getCreatedAt());
    }
}
