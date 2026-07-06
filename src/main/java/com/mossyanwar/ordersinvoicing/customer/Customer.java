package com.mossyanwar.ordersinvoicing.customer;

import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Locale;

@Entity
@Table(name = "customer")
public class Customer {

    public static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;
    public static final int MAX_PAYMENT_TERMS_DAYS = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String billingAddress;

    private int paymentTermsDays;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Customer() {
    }

    public Customer(String name, String email, String billingAddress, int paymentTermsDays, Instant createdAt) {
        this.createdAt = createdAt;
        update(name, email, billingAddress, paymentTermsDays);
    }

    public void update(String name, String email, String billingAddress, int paymentTermsDays) {
        if (paymentTermsDays < 0 || paymentTermsDays > MAX_PAYMENT_TERMS_DAYS) {
            throw new BusinessRuleException("Payment terms must be between 0 and " + MAX_PAYMENT_TERMS_DAYS + " days");
        }
        this.name = name.strip();
        this.email = normaliseEmail(email);
        this.billingAddress = billingAddress.strip();
        this.paymentTermsDays = paymentTermsDays;
    }

    static String normaliseEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
