package com.mossyanwar.ordersinvoicing.invoice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Leave {@code lines} out to credit everything that has not been credited yet. */
public record CreditNoteRequest(
        @NotBlank @Size(max = 500) String reason,
        @Size(max = 100) List<@Valid @NotNull Line> lines) {

    public record Line(@NotNull Long invoiceLineId, @NotNull @Min(1) Integer quantity) {
    }

    /** Quantities keyed by invoice line, adding up repeated lines. */
    Map<Long, Integer> quantitiesByLine() {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        if (lines != null) {
            lines.forEach(line -> quantities.merge(line.invoiceLineId(), line.quantity(), Integer::sum));
        }
        return quantities;
    }
}
