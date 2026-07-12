package com.mossyanwar.ordersinvoicing.order;

import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle:
 * <pre>
 * DRAFT -> CONFIRMED -> FULFILLED -> INVOICED
 *   |          |
 *   +----------+----> CANCELLED
 * </pre>
 * Once goods are fulfilled the order can no longer be cancelled; corrections after that point are
 * made with a credit note against the invoice.
 */
public enum OrderStatus {

    DRAFT,
    CONFIRMED,
    FULFILLED,
    INVOICED,
    CANCELLED;

    public Set<OrderStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(FULFILLED, CANCELLED);
            case FULFILLED -> EnumSet.of(INVOICED);
            case INVOICED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canMoveTo(OrderStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
