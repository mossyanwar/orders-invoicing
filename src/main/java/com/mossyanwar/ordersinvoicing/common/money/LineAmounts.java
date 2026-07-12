package com.mossyanwar.ordersinvoicing.common.money;

import java.util.Collection;

/**
 * Net, VAT and gross for one line (or a sum of lines).
 *
 * <p>Rounding rule: VAT is worked out per line on the line's net amount
 * ({@code unit price x quantity}) and rounded half-up to the penny. Totals are plain sums of the
 * rounded line figures, so an invoice's VAT total always equals the sum of its lines.
 */
public record LineAmounts(Money net, Money vat, Money gross) {

    public static final LineAmounts ZERO = new LineAmounts(Money.ZERO, Money.ZERO, Money.ZERO);

    public LineAmounts {
        if (!net.plus(vat).equals(gross)) {
            throw new IllegalArgumentException("gross must equal net + vat");
        }
    }

    public static LineAmounts of(Money unitPrice, int quantity, VatRate rate) {
        Money net = unitPrice.times(quantity);
        Money vat = rate.vatOn(net);
        return new LineAmounts(net, vat, net.plus(vat));
    }

    public LineAmounts plus(LineAmounts other) {
        return new LineAmounts(net.plus(other.net), vat.plus(other.vat), gross.plus(other.gross));
    }

    public static LineAmounts sum(Collection<LineAmounts> lines) {
        return lines.stream().reduce(ZERO, LineAmounts::plus);
    }
}
