package com.mossyanwar.ordersinvoicing.payment;

import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.invoice.Invoice;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Money received from a customer and how it was split across their invoices. Never changed once saved. */
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, updatable = false)
    private Money amount;

    @Column(nullable = false, updatable = false)
    private LocalDate receivedOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private PaymentMethod method;

    @Column(updatable = false)
    private String reference;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<PaymentAllocation> allocations = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(Long customerId, Money amount, LocalDate receivedOn, PaymentMethod method, String reference, Instant now) {
        this.customerId = customerId;
        this.amount = amount;
        this.receivedOn = receivedOn;
        this.method = method;
        this.reference = reference;
        this.createdAt = now;
    }

    /** Applies part of this payment to an invoice; the invoice refuses if that would overpay it. */
    public void allocateTo(Invoice invoice, Money portion, Instant now) {
        invoice.applyPayment(portion, now);
        allocations.add(new PaymentAllocation(this, invoice, portion));
    }

    public Money allocated() {
        return allocations.stream().map(PaymentAllocation::getAmount).reduce(Money.ZERO, Money::plus);
    }

    public Long getId() {
        return id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public Money getAmount() {
        return amount;
    }

    public LocalDate getReceivedOn() {
        return receivedOn;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public List<PaymentAllocation> getAllocations() {
        return Collections.unmodifiableList(allocations);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
