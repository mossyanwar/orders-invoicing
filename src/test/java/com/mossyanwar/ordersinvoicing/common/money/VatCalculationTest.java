package com.mossyanwar.ordersinvoicing.common.money;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class VatCalculationTest {

    @ParameterizedTest(name = "{1} x {0} at {2} -> net {3}, vat {4}")
    @CsvSource({
            "10.00, 1, STANDARD, 10.00, 2.00",
            "10.00, 3, REDUCED,  30.00, 1.50",
            "10.00, 3, ZERO,     30.00, 0.00",
            "0.13,  1, STANDARD, 0.13,  0.03",   // 0.026 rounds up
            "0.12,  1, STANDARD, 0.12,  0.02",   // 0.024 rounds down
            "0.10,  1, REDUCED,  0.10,  0.01",   // 0.005 is exactly half, rounds up
            "0.09,  1, REDUCED,  0.09,  0.00",   // 0.0045 rounds down
            "19.99, 7, STANDARD, 139.93, 27.99"  // 27.986
    })
    void vatIsRoundedHalfUpPerLine(String unitPrice, int quantity, VatRate rate, String net, String vat) {
        LineAmounts line = LineAmounts.of(Money.of(unitPrice), quantity, rate);

        assertThat(line.net()).isEqualTo(Money.of(net));
        assertThat(line.vat()).isEqualTo(Money.of(vat));
        assertThat(line.gross()).isEqualTo(Money.of(net).plus(Money.of(vat)));
    }

    @Test
    void vatIsWorkedOutOnTheLineNetNotPerUnit() {
        // per unit 0.33 * 20% = 0.066 -> 0.07, times 3 would be 0.21; on the line it is 0.198 -> 0.20
        LineAmounts line = LineAmounts.of(Money.of("0.33"), 3, VatRate.STANDARD);

        assertThat(line.vat()).isEqualTo(Money.of("0.20"));
    }

    @Test
    void totalsAreSumsOfRoundedLines() {
        List<LineAmounts> lines = List.of(
                LineAmounts.of(Money.of("0.13"), 1, VatRate.STANDARD),  // vat 0.03
                LineAmounts.of(Money.of("0.13"), 1, VatRate.STANDARD),  // vat 0.03
                LineAmounts.of(Money.of("4.00"), 2, VatRate.REDUCED),   // vat 0.40
                LineAmounts.of(Money.of("2.50"), 1, VatRate.ZERO));

        LineAmounts total = LineAmounts.sum(lines);

        // VAT on the summed standard-rated net (0.26 * 20% = 0.052) would be 0.05; per-line gives 0.06
        assertThat(total.net()).isEqualTo(Money.of("10.76"));
        assertThat(total.vat()).isEqualTo(Money.of("0.46"));
        assertThat(total.gross()).isEqualTo(Money.of("11.22"));
    }

    @Test
    void sumOfNoLinesIsZero() {
        assertThat(LineAmounts.sum(List.of())).isEqualTo(LineAmounts.ZERO);
    }

    @Test
    void grossMustAddUp() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new LineAmounts(Money.of("1.00"), Money.of("0.20"), Money.of("1.21")));
    }
}
