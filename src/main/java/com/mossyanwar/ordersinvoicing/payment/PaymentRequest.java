package com.mossyanwar.ordersinvoicing.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A payment received. Leave {@code allocations} out to pay the customer's invoices oldest due date
 * first, or list them to say exactly which invoices the money is for.
 */
public record PaymentRequest(
        @NotNull Long customerId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @NotNull @PastOrPresent LocalDate receivedOn,
        @NotNull PaymentMethod method,
        @Size(max = 100) String reference,
        @Size(max = 50) List<@Valid @NotNull Allocation> allocations) {

    public record Allocation(
            @NotNull Long invoiceId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    boolean hasExplicitAllocations() {
        return allocations != null && !allocations.isEmpty();
    }
}
