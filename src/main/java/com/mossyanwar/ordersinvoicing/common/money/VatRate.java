package com.mossyanwar.ordersinvoicing.common.money;

import java.math.BigDecimal;

/** UK VAT rates. Exempt and outside-the-scope supplies are not modelled. */
public enum VatRate {

    STANDARD(new BigDecimal("20")),
    REDUCED(new BigDecimal("5")),
    ZERO(BigDecimal.ZERO);

    private final BigDecimal percent;

    VatRate(BigDecimal percent) {
        this.percent = percent;
    }

    public BigDecimal percent() {
        return percent;
    }

    /** VAT due on a net amount, rounded half-up to the penny. */
    public Money vatOn(Money net) {
        return net.percentage(percent);
    }
}
