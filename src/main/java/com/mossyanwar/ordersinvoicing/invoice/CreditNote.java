package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reduces the amount owed on an invoice. Created only through {@link Invoice#credit}. */
@Entity
@Table(name = "credit_note")
public class CreditNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String creditNoteNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false, updatable = false)
    private Invoice invoice;

    @Column(nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Column(nullable = false, updatable = false)
    private Money netTotal;

    @Column(nullable = false, updatable = false)
    private Money vatTotal;

    @Column(nullable = false, updatable = false)
    private Money grossTotal;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<CreditNoteLine> lines = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CreditNote() {
    }

    CreditNote(Invoice invoice, String creditNoteNumber, LocalDate issueDate, String reason, Instant now) {
        this.invoice = invoice;
        this.creditNoteNumber = creditNoteNumber;
        this.issueDate = issueDate;
        this.reason = reason;
        this.createdAt = now;
        this.netTotal = Money.ZERO;
        this.vatTotal = Money.ZERO;
        this.grossTotal = Money.ZERO;
    }

    void addLine(InvoiceLine invoiceLine, int quantity, LineAmounts amounts) {
        lines.add(new CreditNoteLine(this, invoiceLine, quantity, amounts));
        netTotal = netTotal.plus(amounts.net());
        vatTotal = vatTotal.plus(amounts.vat());
        grossTotal = grossTotal.plus(amounts.gross());
    }

    public Long getId() {
        return id;
    }

    public String getCreditNoteNumber() {
        return creditNoteNumber;
    }

    public Invoice getInvoice() {
        return invoice;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public String getReason() {
        return reason;
    }

    public Money getNetTotal() {
        return netTotal;
    }

    public Money getVatTotal() {
        return vatTotal;
    }

    public Money getGrossTotal() {
        return grossTotal;
    }

    public List<CreditNoteLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
