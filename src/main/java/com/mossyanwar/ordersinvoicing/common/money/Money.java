package com.mossyanwar.ordersinvoicing.common.money;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An amount of pounds sterling, always held at exactly two decimal places.
 *
 * <p>The constructor refuses values with more than two decimal places rather than quietly rounding
 * them, so any rounding in the system has to be asked for explicitly through {@link #rounded} or
 * {@link #percentage}. Those use {@link #ROUNDING} (half-up to the nearest penny).
 */
public record Money(BigDecimal amount) implements Comparable<Money> {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final Money ZERO = new Money(BigDecimal.ZERO);

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    public Money {
        Objects.requireNonNull(amount, "amount");
        if (amount.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException("Money has at most two decimal places, got " + amount.toPlainString());
        }
        amount = amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    /** Rounds any value to the penny using the system rounding rule. */
    public static Money rounded(BigDecimal value) {
        return new Money(value.setScale(SCALE, ROUNDING));
    }

    @JsonValue
    @Override
    public BigDecimal amount() {
        return amount;
    }

    public Money plus(Money other) {
        return new Money(amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(amount.subtract(other.amount));
    }

    /** Exact: a two decimal place amount times a whole number never needs rounding. */
    public Money times(long quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
    }

    /** {@code percent} of this amount, rounded half-up to the penny. */
    public Money percentage(BigDecimal percent) {
        return rounded(amount.multiply(percent).divide(ONE_HUNDRED));
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    @Override
    public int compareTo(Money other) {
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
