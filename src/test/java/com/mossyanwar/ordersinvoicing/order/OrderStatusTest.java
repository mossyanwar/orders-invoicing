package com.mossyanwar.ordersinvoicing.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static com.mossyanwar.ordersinvoicing.order.OrderStatus.CANCELLED;
import static com.mossyanwar.ordersinvoicing.order.OrderStatus.CONFIRMED;
import static com.mossyanwar.ordersinvoicing.order.OrderStatus.DRAFT;
import static com.mossyanwar.ordersinvoicing.order.OrderStatus.FULFILLED;
import static com.mossyanwar.ordersinvoicing.order.OrderStatus.INVOICED;
import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "DRAFT->CONFIRMED",
            "DRAFT->CANCELLED",
            "CONFIRMED->FULFILLED",
            "CONFIRMED->CANCELLED",
            "FULFILLED->INVOICED");

    static Stream<Arguments> everyPair() {
        return Arrays.stream(OrderStatus.values())
                .flatMap(from -> Arrays.stream(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void onlyTheDocumentedTransitionsAreAllowed(OrderStatus from, OrderStatus to) {
        boolean expected = ALLOWED.contains(from + "->" + to);

        assertThat(from.canMoveTo(to)).isEqualTo(expected);
    }

    @Test
    void invoicedAndCancelledAreTerminal() {
        assertThat(INVOICED.isTerminal()).isTrue();
        assertThat(CANCELLED.isTerminal()).isTrue();
        assertThat(DRAFT.isTerminal()).isFalse();
        assertThat(CONFIRMED.isTerminal()).isFalse();
        assertThat(FULFILLED.isTerminal()).isFalse();
    }
}
