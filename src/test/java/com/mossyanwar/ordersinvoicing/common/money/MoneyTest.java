package com.mossyanwar.ordersinvoicing.common.money;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MoneyTest {

    @Test
    void padsToTwoDecimalPlaces() {
        assertThat(Money.of("5").amount()).isEqualByComparingTo("5.00");
        assertThat(Money.of("5").amount().scale()).isEqualTo(2);
        assertThat(Money.of("5.5").toString()).isEqualTo("5.50");
    }

    @Test
    void acceptsTrailingZerosBeyondTwoPlaces() {
        assertThat(Money.of("1.2300")).isEqualTo(Money.of("1.23"));
    }

    @Test
    void refusesFractionsOfAPennyInsteadOfRoundingSilently() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.005"));
    }

    @ParameterizedTest
    @CsvSource({
            "1.004, 1.00",
            "1.005, 1.01",
            "1.015, 1.02",
            "2.675, 2.68",
            "-1.005, -1.01"
    })
    void roundsHalfUpToThePenny(String raw, String expected) {
        assertThat(Money.rounded(new BigDecimal(raw))).isEqualTo(Money.of(expected));
    }

    @Test
    void timesAWholeQuantityIsExact() {
        assertThat(Money.of("0.10").times(3)).isEqualTo(Money.of("0.30"));
        assertThat(Money.of("19.99").times(7)).isEqualTo(Money.of("139.93"));
    }

    @Test
    void percentageRoundsHalfUp() {
        assertThat(Money.of("0.13").percentage(new BigDecimal("20"))).isEqualTo(Money.of("0.03"));   // 0.026
        assertThat(Money.of("0.10").percentage(new BigDecimal("5"))).isEqualTo(Money.of("0.01"));    // 0.005
        assertThat(Money.of("0.09").percentage(new BigDecimal("5"))).isEqualTo(Money.of("0.00"));    // 0.0045
    }

    @Test
    void equalityIgnoresHowTheValueWasWritten() {
        assertThat(Money.of(new BigDecimal("10"))).isEqualTo(Money.of(new BigDecimal("10.00")));
        assertThat(Money.of("10.00")).hasSameHashCodeAs(Money.of("10"));
    }

    @Test
    void comparisons() {
        assertThat(Money.of("2.00").isGreaterThan(Money.of("1.99"))).isTrue();
        assertThat(Money.of("2.00").min(Money.of("1.99"))).isEqualTo(Money.of("1.99"));
        assertThat(Money.of("1.00").minus(Money.of("1.50")).isNegative()).isTrue();
        assertThat(Money.ZERO.isZero()).isTrue();
    }

    @Test
    void serialisesAsAPlainJsonNumber() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(Money.of("12.5"))).isEqualTo("12.50");
        assertThat(mapper.readValue("12.50", Money.class)).isEqualTo(Money.of("12.50"));
    }
}
