package com.mossyanwar.ordersinvoicing.product;

import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        Money unitPrice,
        VatRate vatRate,
        boolean active,
        long version) {

    static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getSku(), product.getName(), product.getUnitPrice(),
                product.getVatRate(), product.isActive(), product.getVersion());
    }
}
