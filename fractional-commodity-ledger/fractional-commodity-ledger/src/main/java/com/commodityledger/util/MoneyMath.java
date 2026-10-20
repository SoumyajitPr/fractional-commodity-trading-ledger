package com.commodityledger.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Every monetary and quantity calculation goes through this class, so the rounding
 * policy lives in exactly one place.
 *
 * <ul>
 *   <li>Money has 2 decimal places and is rounded HALF_EVEN (banker's rounding).</li>
 *   <li>Quantity has 6 decimal places and is always rounded DOWN, so a user never
 *       receives more commodity than they paid for.</li>
 * </ul>
 *
 * float/double are never used: 0.1 + 0.2 != 0.3 in binary floating point.
 */
public final class MoneyMath {

    public static final int MONEY_SCALE = 2;
    public static final int QUANTITY_SCALE = 6;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_EVEN;

    private MoneyMath() {
    }

    /** Normalises a money value to 2 decimal places. */
    public static BigDecimal money(BigDecimal value) {
        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    /** Normalises a quantity to 6 decimal places (rounding down). */
    public static BigDecimal quantity(BigDecimal value) {
        return value.setScale(QUANTITY_SCALE, RoundingMode.DOWN);
    }

    /** Money value of {@code quantity} units at {@code pricePerUnit}. */
    public static BigDecimal value(BigDecimal quantity, BigDecimal pricePerUnit) {
        return money(quantity.multiply(pricePerUnit));
    }

    /**
     * Quantity that {@code amount} buys at {@code pricePerUnit}, rounded down.
     * Because the quantity is rounded down, {@code value(result, price) <= amount}.
     */
    public static BigDecimal quantityForAmount(BigDecimal amount, BigDecimal pricePerUnit) {
        return amount.divide(pricePerUnit, QUANTITY_SCALE, RoundingMode.DOWN);
    }

    /**
     * Share of {@code totalCost} attributable to {@code soldQuantity} out of {@code totalQuantity}
     * (average-cost method). Selling the whole position releases the whole cost exactly,
     * so no rounding residue is ever left behind.
     */
    public static BigDecimal proportionalCost(BigDecimal totalCost, BigDecimal totalQuantity, BigDecimal soldQuantity) {
        if (soldQuantity.compareTo(totalQuantity) >= 0) {
            return money(totalCost);
        }
        return totalCost.multiply(soldQuantity).divide(totalQuantity, MONEY_SCALE, MONEY_ROUNDING);
    }

    /** Average cost per unit, or zero for an empty position. */
    public static BigDecimal averageCost(BigDecimal totalCost, BigDecimal totalQuantity) {
        if (totalQuantity.signum() == 0) {
            return money(BigDecimal.ZERO);
        }
        return totalCost.divide(totalQuantity, MONEY_SCALE, MONEY_ROUNDING);
    }
}
