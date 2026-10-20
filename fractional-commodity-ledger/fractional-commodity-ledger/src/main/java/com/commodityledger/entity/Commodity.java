package com.commodityledger.entity;

import com.commodityledger.util.MoneyMath;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "commodities")
public class Commodity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol", nullable = false, unique = true, length = 16)
    private String symbol;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Unit the price refers to, e.g. GRAM. */
    @Column(name = "unit", nullable = false, length = 16)
    private String unit;

    @Column(name = "price_per_unit", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePerUnit;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Commodity() {
        // for JPA
    }

    public void updatePrice(BigDecimal newPrice) {
        if (newPrice == null || newPrice.signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        this.pricePerUnit = MoneyMath.money(newPrice);
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getPricePerUnit() {
        return pricePerUnit;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
