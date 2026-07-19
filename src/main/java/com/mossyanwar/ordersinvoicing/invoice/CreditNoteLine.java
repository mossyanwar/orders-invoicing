package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
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

@Entity
@Table(name = "credit_note_line")
public class CreditNoteLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false, updatable = false)
    private CreditNote creditNote;

    @Column(nullable = false, updatable = false)
    private Long invoiceLineId;

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

    protected CreditNoteLine() {
    }

    CreditNoteLine(CreditNote creditNote, InvoiceLine invoiceLine, int quantity, LineAmounts amounts) {
        this.creditNote = creditNote;
        this.invoiceLineId = invoiceLine.getId();
        this.sku = invoiceLine.getSku();
        this.description = invoiceLine.getDescription();
        this.quantity = quantity;
        this.unitPrice = invoiceLine.getUnitPrice();
        this.vatRate = invoiceLine.getVatRate();
        this.netAmount = amounts.net();
        this.vatAmount = amounts.vat();
        this.grossAmount = amounts.gross();
    }

    public Long getId() {
        return id;
    }

    public Long getInvoiceLineId() {
        return invoiceLineId;
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
