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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An executed trade. Only successful trades are stored: a failed trade rolls back
 * together with every other change made in its database transaction.
 */
@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commodity_id", nullable = false, updatable = false)
    private Commodity commodity;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 4, updatable = false)
    private TradeSide side;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 6, updatable = false)
    private BigDecimal quantity;

    @Column(name = "price_per_unit", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal pricePerUnit;

    /** Cost of a BUY, or proceeds of a SELL. */
    @Column(name = "gross_amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal grossAmount;

    /** SELL only: cost basis removed from the holding. */
    @Column(name = "cost_basis_released", precision = 19, scale = 2, updatable = false)
    private BigDecimal costBasisReleased;

    /** SELL only: proceeds minus cost basis released. */
    @Column(name = "realized_pnl", precision = 19, scale = 2, updatable = false)
    private BigDecimal realizedPnl;

    @Column(name = "client_order_id", length = 64, updatable = false)
    private String clientOrderId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Trade() {
        // for JPA
    }

    private Trade(User user, Commodity commodity, TradeSide side, BigDecimal quantity, BigDecimal pricePerUnit,
                  BigDecimal grossAmount, BigDecimal costBasisReleased, BigDecimal realizedPnl, String clientOrderId) {
        this.user = user;
        this.commodity = commodity;
        this.side = side;
        this.quantity = quantity;
        this.pricePerUnit = pricePerUnit;
        this.grossAmount = grossAmount;
        this.costBasisReleased = costBasisReleased;
        this.realizedPnl = realizedPnl;
        this.clientOrderId = clientOrderId;
    }

    public static Trade buy(User user, Commodity commodity, BigDecimal quantity, BigDecimal price,
                            BigDecimal cost, String clientOrderId) {
        return new Trade(user, commodity, TradeSide.BUY, quantity, price, cost, null, null, clientOrderId);
    }

    public static Trade sell(User user, Commodity commodity, BigDecimal quantity, BigDecimal price,
                             BigDecimal proceeds, BigDecimal costBasisReleased, BigDecimal realizedPnl,
                             String clientOrderId) {
        return new Trade(user, commodity, TradeSide.SELL, quantity, price, proceeds,
                costBasisReleased, realizedPnl, clientOrderId);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Commodity getCommodity() {
        return commodity;
    }

    public TradeSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public BigDecimal getCostBasisReleased() {
        return costBasisReleased;
    }

    public BigDecimal getRealizedPnl() {
        return realizedPnl;
    }

    public String getClientOrderId() {
        return clientOrderId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
