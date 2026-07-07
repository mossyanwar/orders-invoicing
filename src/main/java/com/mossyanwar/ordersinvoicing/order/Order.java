package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.error.ConflictException;
import com.mossyanwar.ordersinvoicing.common.error.InvalidStateTransitionException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.customer.Customer;
import com.mossyanwar.ordersinvoicing.product.Product;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root for an order and its lines. All changes go through methods on this class so the
 * lifecycle rules in {@link OrderStatus} cannot be bypassed. Any change also touches
 * {@code updatedAt}, which bumps {@code version} and so protects the whole aggregate with one
 * optimistic lock.
 */
@Entity
@Table(name = "orders")
public class Order {

    public static final int MAX_LINES = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderLine> lines = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Order() {
    }

    public Order(Customer customer, Instant now) {
        this.customer = Objects.requireNonNull(customer);
        this.status = OrderStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public OrderLine addLine(Product product, int quantity, Instant now) {
        requireDraft();
        if (!product.isActive()) {
            throw new BusinessRuleException("Product " + product.getSku() + " is no longer sold");
        }
        if (quantity <= 0) {
            throw new BusinessRuleException("Quantity must be at least 1");
        }
        if (lines.size() >= MAX_LINES) {
            throw new BusinessRuleException("An order can have at most " + MAX_LINES + " lines");
        }
        OrderLine line = new OrderLine(this, product, quantity);
        lines.add(line);
        updatedAt = now;
        return line;
    }

    public void removeLine(long lineId, Instant now) {
        requireDraft();
        boolean removed = lines.removeIf(line -> Objects.equals(line.getId(), lineId));
        if (!removed) {
            throw new NotFoundException("Order line", lineId);
        }
        updatedAt = now;
    }

    public void confirm(Instant now) {
        if (status == OrderStatus.DRAFT && lines.isEmpty()) {
            throw new BusinessRuleException("An order needs at least one line before it can be confirmed");
        }
        moveTo(OrderStatus.CONFIRMED, now);
    }

    public void fulfil(Instant now) {
        moveTo(OrderStatus.FULFILLED, now);
    }

    public void cancel(Instant now) {
        moveTo(OrderStatus.CANCELLED, now);
    }

    /** Called by invoicing in the same transaction that creates the invoice. */
    public void markInvoiced(Instant now) {
        moveTo(OrderStatus.INVOICED, now);
    }

    public LineAmounts totals() {
        return LineAmounts.sum(lines.stream().map(OrderLine::amounts).toList());
    }

    private void moveTo(OrderStatus next, Instant now) {
        if (!status.canMoveTo(next)) {
            throw new InvalidStateTransitionException("Order", id, status, next);
        }
        status = next;
        updatedAt = now;
    }

    private void requireDraft() {
        if (status != OrderStatus.DRAFT) {
            throw new ConflictException("Order %s is %s; lines can only be changed while it is DRAFT".formatted(id, status));
        }
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderLine> getLines() {
        return Collections.unmodifiableList(lines);
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
