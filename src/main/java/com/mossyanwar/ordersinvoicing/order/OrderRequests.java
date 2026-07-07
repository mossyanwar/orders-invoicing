package com.mossyanwar.ordersinvoicing.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class OrderRequests {

    private OrderRequests() {
    }

    public record Create(
            @NotNull Long customerId,
            @Size(max = Order.MAX_LINES) List<@Valid @NotNull Line> lines) {

        public List<Line> linesOrEmpty() {
            return lines == null ? List.of() : lines;
        }
    }

    public record Line(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(10_000) Integer quantity) {
    }
}
