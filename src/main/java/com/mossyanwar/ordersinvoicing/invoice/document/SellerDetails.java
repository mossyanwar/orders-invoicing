package com.mossyanwar.ordersinvoicing.invoice.document;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The business issuing the invoices, printed at the top of every PDF. */
@Validated
@ConfigurationProperties("app.seller")
public record SellerDetails(
        @NotBlank String name,
        @NotBlank String address,
        @NotBlank String vatNumber,
        @NotBlank String email) {
}
