package com.commodityledger.entity;

import com.commodityledger.util.MoneyMath;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A user's position in one commodity. {@code costBasis} is the total rupee cost of the
 * quantity currently held (average-cost method).
 */
@Entity
@Table(name = "holdings")
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

    @Column(name = "quantity", nullable = false, precision = 20, scale = 6)
    private BigDecimal quantity;

    @Column(name = "cost_basis", nullable = false, precision = 19, scale = 2)
    private BigDecimal costBasis;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Holding() {
        // for JPA
    }

    public Holding(User user, Commodity commodity) {
        this.user = user;
        this.commodity = commodity;
        this.quantity = MoneyMath.quantity(BigDecimal.ZERO);
        this.costBasis = MoneyMath.money(BigDecimal.ZERO);
    }

    public void addPurchase(BigDecimal boughtQuantity, BigDecimal cost) {
        quantity = MoneyMath.quantity(quantity.add(boughtQuantity));
        costBasis = MoneyMath.money(costBasis.add(cost));
    }

    public void removeSale(BigDecimal soldQuantity, BigDecimal costReleased) {
        if (soldQuantity.compareTo(quantity) > 0 || costReleased.compareTo(costBasis) > 0) {
            throw new IllegalStateException("Cannot remove more than the holding contains");
        }
        quantity = MoneyMath.quantity(quantity.subtract(soldQuantity));
        costBasis = MoneyMath.money(costBasis.subtract(costReleased));
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

    public Commodity getCommodity() {
        return commodity;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getCostBasis() {
        return costBasis;
    }
}
