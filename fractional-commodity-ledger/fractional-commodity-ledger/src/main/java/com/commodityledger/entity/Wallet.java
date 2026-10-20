package com.commodityledger.entity;

import com.commodityledger.util.MoneyMath;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallets")
public class Wallet {

    public static final String DEFAULT_CURRENCY = "INR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
        // for JPA
    }

    public Wallet(User user) {
        this.user = user;
        this.balance = MoneyMath.money(BigDecimal.ZERO);
        this.currency = DEFAULT_CURRENCY;
    }

    public boolean hasAtLeast(BigDecimal amount) {
        return balance.compareTo(amount) >= 0;
    }

    public void credit(BigDecimal amount) {
        requirePositive(amount);
        balance = MoneyMath.money(balance.add(amount));
    }

    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!hasAtLeast(amount)) {
            // Services check this first and throw a user-facing error; this is a safety net.
            throw new IllegalStateException("Wallet balance would become negative");
        }
        balance = MoneyMath.money(balance.subtract(amount));
    }

    private static void requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
