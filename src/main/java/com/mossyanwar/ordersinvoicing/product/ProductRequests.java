package com.mossyanwar.ordersinvoicing.product;

import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public final class ProductRequests {

    private ProductRequests() {
    }

    public record Create(
            @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9-]+", message = "letters, digits and dashes only") String sku,
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull VatRate vatRate) {
    }

    public record Update(
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @NotNull VatRate vatRate,
            @NotNull Boolean active,
            @NotNull Long version) {
    }
}
