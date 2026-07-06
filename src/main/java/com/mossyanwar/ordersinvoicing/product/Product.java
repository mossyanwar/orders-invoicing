package com.mossyanwar.ordersinvoicing.product;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Locale;

/**
 * A catalogue entry. Changing a price here does not change existing orders: order lines copy the
 * price and VAT rate when they are added.
 */
@Entity
@Table(name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Money unitPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VatRate vatRate;

    private boolean active;

    @Version
    private long version;

    protected Product() {
    }

    public Product(String sku, String name, Money unitPrice, VatRate vatRate) {
        this.sku = sku.strip().toUpperCase(Locale.ROOT);
        this.active = true;
        update(name, unitPrice, vatRate, true);
    }

    public void update(String name, Money unitPrice, VatRate vatRate, boolean active) {
        if (unitPrice.isNegative()) {
            throw new BusinessRuleException("Unit price cannot be negative");
        }
        this.name = name.strip();
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getSku() {
        return sku;
    }

    public String getName() {
        return name;
    }

    public Money getUnitPrice() {
        return unitPrice;
    }

    public VatRate getVatRate() {
        return vatRate;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
