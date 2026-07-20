package com.mossyanwar.ordersinvoicing.payment;

import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.invoice.Invoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_allocation")
public class PaymentAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    private Payment payment;

    @Column(nullable = false, updatable = false)
    private Long invoiceId;

    @Column(nullable = false, updatable = false)
    private String invoiceNumber;

    @Column(nullable = false, updatable = false)
    private Money amount;

    protected PaymentAllocation() {
    }

    PaymentAllocation(Payment payment, Invoice invoice, Money amount) {
        this.payment = payment;
        this.invoiceId = invoice.getId();
        this.invoiceNumber = invoice.getInvoiceNumber();
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public Money getAmount() {
        return amount;
    }
}
