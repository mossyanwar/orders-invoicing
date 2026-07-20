package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.order.OrderLine;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A line on an invoice. The amounts are stored as issued, not recomputed. */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    private int lineNumber;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false, updatable = false)
    private String description;

    private int quantity;

    @Column(nullable = false, updatable = false)
    private Money unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private VatRate vatRate;

    @Column(nullable = false, updatable = false)
    private Money netAmount;

    @Column(nullable = false, updatable = false)
    private Money vatAmount;

    @Column(nullable = false, updatable = false)
    private Money grossAmount;

    private int creditedQuantity;

    protected InvoiceLine() {
    }

    InvoiceLine(Invoice invoice, int lineNumber, OrderLine orderLine) {
        this.invoice = invoice;
        this.lineNumber = lineNumber;
        this.sku = orderLine.getSku();
        this.description = orderLine.getDescription();
        this.quantity = orderLine.getQuantity();
        this.unitPrice = orderLine.getUnitPrice();
        this.vatRate = orderLine.getVatRate();
        LineAmounts amounts = LineAmounts.of(unitPrice, quantity, vatRate);
        this.netAmount = amounts.net();
        this.vatAmount = amounts.vat();
        this.grossAmount = amounts.gross();
    }

    public LineAmounts amounts() {
        return new LineAmounts(netAmount, vatAmount, grossAmount);
    }

    public int remainingQuantity() {
        return quantity - creditedQuantity;
    }

    /**
     * What crediting {@code count} more units of this line is worth, without changing anything.
     *
     * <p>VAT is rounded on the running total credited so far, and this credit is the difference.
     * Rounding each credit on its own would drift: five separate credits of one 0.10 unit at 5%
     * would each round 0.005 up to 0.01 and refund 0.05 of VAT on a line that only charged 0.03.
     * Working from the cumulative figure means the credits always add up to exactly the line's VAT
     * once everything has been credited.
     */
    LineAmounts creditValue(int count) {
        if (count < 1 || count > remainingQuantity()) {
            throw new BusinessRuleException("Line %d has %d left to credit, so %d cannot be credited"
                    .formatted(lineNumber, remainingQuantity(), count));
        }
        LineAmounts before = LineAmounts.of(unitPrice, creditedQuantity, vatRate);
        LineAmounts after = LineAmounts.of(unitPrice, creditedQuantity + count, vatRate);
        Money net = after.net().minus(before.net());
        Money vat = after.vat().minus(before.vat());
        return new LineAmounts(net, vat, net.plus(vat));
    }

    void recordCredit(int count) {
        creditValue(count);
        creditedQuantity += count;
    }

    public Long getId() {
        return id;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getSku() {
        return sku;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public VatRate getVatRate() {
        return vatRate;
    }

    public Money getNetAmount() {
        return netAmount;
    }

    public Money getVatAmount() {
        return vatAmount;
    }

    public Money getGrossAmount() {
        return grossAmount;
    }

    public int getCreditedQuantity() {
        return creditedQuantity;
    }
}
