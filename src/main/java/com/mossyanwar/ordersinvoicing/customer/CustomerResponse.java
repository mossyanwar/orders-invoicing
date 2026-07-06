package com.mossyanwar.ordersinvoicing.customer;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String name,
        String email,
        String billingAddress,
        int paymentTermsDays,
        Instant createdAt,
        long version) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getEmail(),
                customer.getBillingAddress(), customer.getPaymentTermsDays(), customer.getCreatedAt(),
                customer.getVersion());
    }
}
