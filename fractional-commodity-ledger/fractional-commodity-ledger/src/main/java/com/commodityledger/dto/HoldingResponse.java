package com.commodityledger.dto;

import java.math.BigDecimal;

public record HoldingResponse(
        String commodity,
        String name,
        String unit,
        BigDecimal quantity,
        BigDecimal averageCost,
        BigDecimal costBasis,
        BigDecimal currentPrice,
        BigDecimal marketValue,
        BigDecimal unrealizedPnl) {
}
