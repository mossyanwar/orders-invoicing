package com.mossyanwar.ordersinvoicing.customer;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CustomerRequests {

    private CustomerRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 500) String billingAddress,
            @Min(0) @Max(Customer.MAX_PAYMENT_TERMS_DAYS) Integer paymentTermsDays) {

        int paymentTermsOrDefault() {
            return paymentTermsDays == null ? Customer.DEFAULT_PAYMENT_TERMS_DAYS : paymentTermsDays;
        }
    }

    public record Update(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 500) String billingAddress,
            @NotNull @Min(0) @Max(Customer.MAX_PAYMENT_TERMS_DAYS) Integer paymentTermsDays,
            @NotNull Long version) {
    }
}
