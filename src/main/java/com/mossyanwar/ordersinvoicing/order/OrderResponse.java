package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;

import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        List<Line> lines,
        Money netTotal,
        Money vatTotal,
        Money grossTotal,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public record Line(
            Long id,
            Long productId,
            String sku,
            String description,
            int quantity,
            Money unitPrice,
            VatRate vatRate,
            Money net,
            Money vat,
            Money gross) {

        static Line from(OrderLine line) {
            LineAmounts amounts = line.amounts();
            return new Line(line.getId(), line.getProductId(), line.getSku(), line.getDescription(),
                    line.getQuantity(), line.getUnitPrice(), line.getVatRate(),
                    amounts.net(), amounts.vat(), amounts.gross());
        }
    }

    public static OrderResponse from(Order order) {
        LineAmounts totals = order.totals();
        return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getStatus(),
                order.getLines().stream().map(Line::from).toList(),
                totals.net(), totals.vat(), totals.gross(),
                order.getCreatedAt(), order.getUpdatedAt(), order.getVersion());
    }
}
