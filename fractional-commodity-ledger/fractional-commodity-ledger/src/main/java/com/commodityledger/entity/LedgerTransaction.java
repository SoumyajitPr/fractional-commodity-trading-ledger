package com.commodityledger.entity;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One journal entry: the group of ledger entries produced by a single business event.
 * Rows are never updated (every column is non-updatable; PostgreSQL also enforces this
 * with a trigger).
 */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16, updatable = false)
    private LedgerTransactionType type;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "trade_id", updatable = false)
    private Long tradeId;

    @Column(name = "description", nullable = false, length = 255, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "ledgerTransaction", cascade = CascadeType.PERSIST)
    @OrderBy("id ASC")
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
        // for JPA
    }

    public LedgerTransaction(LedgerTransactionType type, Long userId, Long tradeId, String description) {
        this.type = type;
        this.userId = userId;
        this.tradeId = tradeId;
        this.description = description;
    }

    public void addEntry(LedgerEntry entry) {
        entry.attachTo(this);
        entries.add(entry);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LedgerTransactionType getType() {
        return type;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getTradeId() {
        return tradeId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
}
