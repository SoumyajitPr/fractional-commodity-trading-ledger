package com.commodityledger.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One line of a journal: a DEBIT or CREDIT of a positive amount to one account.
 * An account is identified by (accountType, userId, commoditySymbol).
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ledger_transaction_id", nullable = false, updatable = false)
    private LedgerTransaction ledgerTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32, updatable = false)
    private LedgerAccountType accountType;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "commodity_symbol", length = 16, updatable = false)
    private String commoditySymbol;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6, updatable = false)
    private EntryDirection direction;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // for JPA
    }

    public LedgerEntry(LedgerAccountType accountType, Long userId, String commoditySymbol,
                       EntryDirection direction, BigDecimal amount) {
        this.accountType = accountType;
        this.userId = userId;
        this.commoditySymbol = commoditySymbol;
        this.direction = direction;
        this.amount = amount;
    }

    void attachTo(LedgerTransaction transaction) {
        this.ledgerTransaction = transaction;
    }

    /** Human-readable account code, e.g. {@code USER_COMMODITY:7:GOLD}. */
    public String accountCode() {
        StringBuilder code = new StringBuilder(accountType.name());
        if (userId != null) {
            code.append(':').append(userId);
        }
        if (commoditySymbol != null) {
            code.append(':').append(commoditySymbol);
        }
        return code.toString();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LedgerAccountType getAccountType() {
        return accountType;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCommoditySymbol() {
        return commoditySymbol;
    }

    public EntryDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
