package com.commodityledger.dto;

import java.math.BigDecimal;

/**
 * Result of POST /api/trades/buy or /sell.
 *
 * @param walletBalance   the user's wallet balance after the trade
 * @param holdingQuantity the user's quantity of this commodity after the trade
 * @param replayed        true when the clientOrderId had already been used and the
 *                        original trade is being returned instead of trading again
 */
public record TradeExecutionResponse(
        TradeResponse trade,
        BigDecimal walletBalance,
        BigDecimal holdingQuantity,
        boolean replayed) {
}
