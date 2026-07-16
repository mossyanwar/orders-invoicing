package com.mossyanwar.ordersinvoicing.invoice;

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
}
