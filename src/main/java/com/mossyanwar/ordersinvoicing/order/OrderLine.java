package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.common.money.LineAmounts;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.product.Product;
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

/** A line on an order, holding a snapshot of the product's price and VAT rate. */
@Entity
@Table(name = "order_line")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    @Column(nullable = false, updatable = false)
    private Long productId;

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

    protected OrderLine() {
    }

    OrderLine(Order order, Product product, int quantity) {
        this.order = order;
        this.productId = product.getId();
        this.sku = product.getSku();
        this.description = product.getName();
        this.quantity = quantity;
        this.unitPrice = product.getUnitPrice();
        this.vatRate = product.getVatRate();
    }

    public LineAmounts amounts() {
        return LineAmounts.of(unitPrice, quantity, vatRate);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
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
}
