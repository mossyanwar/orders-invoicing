package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.customer.Customer;
import com.mossyanwar.ordersinvoicing.order.Order;
import com.mossyanwar.ordersinvoicing.order.OrderLine;
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
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An issued invoice. Invoices are never edited or deleted once issued: money comes off them through
 * payments and credit notes, and the status follows from what is still owed.
 */
@Entity
@Table(name = "invoice")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String invoiceNumber;

    @Column(nullable = false, updatable = false)
    private Long orderId;

    @Column(nullable = false, updatable = false)
    private Long customerId;

    @Column(nullable = false, updatable = false)
    private String customerName;

    @Column(nullable = false, updatable = false)
    private String billingAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDate issueDate;

    @Column(nullable = false, updatable = false)
    private LocalDate dueDate;

    private int paymentTermsDays;

    @Column(nullable = false, updatable = false)
    private Money netTotal;

    @Column(nullable = false, updatable = false)
    private Money vatTotal;

    @Column(nullable = false, updatable = false)
    private Money grossTotal;

    @Column(nullable = false)
    private Money amountPaid;

    @Column(nullable = false)
    private Money amountCredited;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @OrderBy("lineNumber")
    private List<InvoiceLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<CreditNote> creditNotes = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Invoice() {
    }

    /**
     * Builds the invoice for a fulfilled order. The caller is responsible for moving the order to
     * INVOICED and for allocating {@code invoiceNumber} in the same transaction.
     */
    public static Invoice issue(Order order, String invoiceNumber, LocalDate issueDate, Instant now) {
        Customer customer = order.getCustomer();
        Invoice invoice = new Invoice();
        invoice.invoiceNumber = invoiceNumber;
        invoice.orderId = order.getId();
        invoice.customerId = customer.getId();
        invoice.customerName = customer.getName();
        invoice.billingAddress = customer.getBillingAddress();
        invoice.issueDate = issueDate;
        invoice.paymentTermsDays = customer.getPaymentTermsDays();
        invoice.dueDate = issueDate.plusDays(customer.getPaymentTermsDays());
        int lineNumber = 1;
        for (OrderLine orderLine : order.getLines()) {
            invoice.lines.add(new InvoiceLine(invoice, lineNumber++, orderLine));
        }
        LineAmounts totals = LineAmounts.sum(invoice.lines.stream().map(InvoiceLine::amounts).toList());
        invoice.netTotal = totals.net();
        invoice.vatTotal = totals.vat();
        invoice.grossTotal = totals.gross();
        invoice.amountPaid = Money.ZERO;
        invoice.amountCredited = Money.ZERO;
        invoice.status = totals.gross().isZero() ? InvoiceStatus.PAID : InvoiceStatus.ISSUED;
        invoice.createdAt = now;
        invoice.updatedAt = now;
        return invoice;
    }

    public Money outstanding() {
        return grossTotal.minus(amountPaid).minus(amountCredited);
    }

    /**
     * Takes a payment off the balance. Paying more than is outstanding is refused rather than
     * leaving the customer in credit, because customer credit balances are not modelled.
     */
    public void applyPayment(Money amount, Instant now) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("Invoice %s is %s and cannot take payments".formatted(invoiceNumber, status));
        }
        if (!amount.isPositive()) {
            throw new BusinessRuleException("Payment amounts must be positive");
        }
        if (amount.isGreaterThan(outstanding())) {
            throw new BusinessRuleException("Payment of %s is more than the %s outstanding on invoice %s"
                    .formatted(amount, outstanding(), invoiceNumber));
        }
        amountPaid = amountPaid.plus(amount);
        if (outstanding().isZero()) {
            status = InvoiceStatus.PAID;
        } else if (status != InvoiceStatus.OVERDUE) {
            status = InvoiceStatus.PART_PAID;
        }
        updatedAt = now;
    }

    /**
     * Issues a credit note against some or all of the lines. An empty {@code requested} map credits
     * everything not yet credited. {@code nextNumber} is only called once the request has passed
     * every check, so the lock behind the gapless counter is taken as late as possible.
     *
     * <p>A credit can only take the balance down to zero: crediting money the customer has already
     * paid would mean a refund, and refunds are not modelled. An invoice credited in full before any
     * payment becomes VOID; one whose balance reaches zero through a mix of payments and credits is
     * PAID.
     */
    public CreditNote credit(Map<Long, Integer> requested, String reason, LocalDate issueDate, Instant now,
                             Supplier<String> nextNumber) {
        if (!status.isOpen()) {
            throw new BusinessRuleException("Invoice %s is %s; only open invoices can be credited"
                    .formatted(invoiceNumber, status));
        }
        Map<InvoiceLine, Integer> plan = creditPlan(requested);
        Map<InvoiceLine, LineAmounts> values = new LinkedHashMap<>();
        plan.forEach((line, count) -> values.put(line, line.creditValue(count)));
        LineAmounts total = LineAmounts.sum(values.values());
        if (total.gross().isGreaterThan(outstanding())) {
            throw new BusinessRuleException(("Credit of %s is more than the %s outstanding on invoice %s; "
                    + "refunds of money already paid are not supported").formatted(total.gross(), outstanding(), invoiceNumber));
        }

        CreditNote note = new CreditNote(this, nextNumber.get(), issueDate, reason, now);
        plan.forEach((line, count) -> {
            note.addLine(line, count, values.get(line));
            line.recordCredit(count);
        });
        creditNotes.add(note);
        amountCredited = amountCredited.plus(total.gross());
        if (lines.stream().allMatch(line -> line.remainingQuantity() == 0)) {
            status = InvoiceStatus.VOID;
        } else if (outstanding().isZero()) {
            status = InvoiceStatus.PAID;
        }
        updatedAt = now;
        return note;
    }

    private Map<InvoiceLine, Integer> creditPlan(Map<Long, Integer> requested) {
        Map<InvoiceLine, Integer> plan = new LinkedHashMap<>();
        if (requested.isEmpty()) {
            lines.stream().filter(line -> line.remainingQuantity() > 0)
                    .forEach(line -> plan.put(line, line.remainingQuantity()));
        } else {
            requested.forEach((lineId, count) -> plan.put(line(lineId), count));
        }
        if (plan.isEmpty()) {
            throw new BusinessRuleException("Invoice " + invoiceNumber + " has nothing left to credit");
        }
        return plan;
    }

    private InvoiceLine line(Long lineId) {
        return lines.stream()
                .filter(line -> Objects.equals(line.getId(), lineId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Line on invoice " + invoiceNumber, lineId));
    }

    /** Returns true if the invoice became overdue as a result of this call. */
    public boolean markOverdueIfPastDue(LocalDate today, Instant now) {
        boolean pastDue = dueDate.isBefore(today);
        if (pastDue && (status == InvoiceStatus.ISSUED || status == InvoiceStatus.PART_PAID)) {
            status = InvoiceStatus.OVERDUE;
            updatedAt = now;
            return true;
        }
        return false;
    }

    /** Net and VAT per rate, as a VAT invoice has to show them. */
    public Map<VatRate, LineAmounts> vatBreakdown() {
        Map<VatRate, LineAmounts> breakdown = new EnumMap<>(VatRate.class);
        for (InvoiceLine line : lines) {
            breakdown.merge(line.getVatRate(), line.amounts(), LineAmounts::plus);
        }
        return breakdown;
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
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

    public Money getAmountPaid() {
        return amountPaid;
    }

    public Money getAmountCredited() {
        return amountCredited;
    }

    public List<InvoiceLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public List<CreditNote> getCreditNotes() {
        return Collections.unmodifiableList(creditNotes);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
