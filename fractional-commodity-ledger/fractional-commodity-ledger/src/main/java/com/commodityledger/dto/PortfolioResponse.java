package com.commodityledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioResponse(
        Long userId,
        BigDecimal cashBalance,
        List<HoldingResponse> holdings,
        BigDecimal totalCostBasis,
        BigDecimal totalMarketValue,
        BigDecimal totalUnrealizedPnl,
        BigDecimal totalRealizedPnl,
        BigDecimal netWorth,
        Instant valuedAt) {
}
