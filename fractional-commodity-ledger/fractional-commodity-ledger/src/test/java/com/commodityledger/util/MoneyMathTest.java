package com.commodityledger.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyMathTest {

    @Test
    void doubleArithmeticIsWrongForMoneyButBigDecimalIsExact() {
        assertThat(0.1 + 0.2).isNotEqualTo(0.3);
        assertThat(new BigDecimal("0.1").add(new BigDecimal("0.2"))).isEqualByComparingTo("0.3");
    }

    @Test
    void walletExampleFromTheSpec() {
        BigDecimal balance = new BigDecimal("10000.00");
        BigDecimal price = new BigDecimal("2000.00");
        assertThat(balance.subtract(price)).isEqualTo(new BigDecimal("8000.00"));
    }

    @Test
    void investing500InGoldBuysFourCentigrams() {
        BigDecimal quantity = MoneyMath.quantityForAmount(new BigDecimal("500.00"), new BigDecimal("12500.00"));
        assertThat(quantity).isEqualTo(new BigDecimal("0.040000"));
        assertThat(MoneyMath.value(quantity, new BigDecimal("12500.00"))).isEqualTo(new BigDecimal("500.00"));
    }

    @Test
    void quantityIsRoundedDownSoCostNeverExceedsTheAmount() {
        BigDecimal amount = new BigDecimal("100.00");
        BigDecimal price = new BigDecimal("3.00");
        BigDecimal quantity = MoneyMath.quantityForAmount(amount, price);

        assertThat(quantity).isEqualTo(new BigDecimal("33.333333"));
        assertThat(MoneyMath.value(quantity, price)).isLessThanOrEqualTo(amount);
    }

    @Test
    void buyingByAmountNeverCostsMoreThanTheAmount() {
        java.util.Random random = new java.util.Random(42);
        for (int i = 0; i < 200_000; i++) {
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(5_000_000) + 1, 2);   // 0.01 .. 50,000.00
            BigDecimal price = BigDecimal.valueOf(random.nextInt(2_000_000) + 1, 2);    // 0.01 .. 20,000.00
            BigDecimal quantity = MoneyMath.quantityForAmount(amount, price);
            assertThat(MoneyMath.value(quantity, price)).isLessThanOrEqualTo(amount);
        }
    }

    @Test
    void moneyUsesBankersRounding() {
        assertThat(MoneyMath.money(new BigDecimal("2.345"))).isEqualTo(new BigDecimal("2.34"));
        assertThat(MoneyMath.money(new BigDecimal("2.355"))).isEqualTo(new BigDecimal("2.36"));
    }

    @Test
    void proportionalCostUsesAverageCost() {
        BigDecimal cost = MoneyMath.proportionalCost(new BigDecimal("3200.00"), new BigDecimal("1.000000"),
                new BigDecimal("0.500000"));
        assertThat(cost).isEqualTo(new BigDecimal("1600.00"));
    }

    @Test
    void sellingEverythingReleasesTheWholeCostWithNoResidue() {
        BigDecimal cost = MoneyMath.proportionalCost(new BigDecimal("100.00"), new BigDecimal("3.000000"),
                new BigDecimal("3.000000"));
        assertThat(cost).isEqualTo(new BigDecimal("100.00"));
    }
}
