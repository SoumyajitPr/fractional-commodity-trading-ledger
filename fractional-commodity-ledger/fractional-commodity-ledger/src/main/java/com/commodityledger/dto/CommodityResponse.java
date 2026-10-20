package com.commodityledger.dto;

import com.commodityledger.entity.Commodity;

import java.math.BigDecimal;
import java.time.Instant;

public record CommodityResponse(
        String symbol,
        String name,
        String unit,
        BigDecimal pricePerUnit,
        Instant updatedAt) {

    public static CommodityResponse from(Commodity commodity) {
        return new CommodityResponse(commodity.getSymbol(), commodity.getName(), commodity.getUnit(),
                commodity.getPricePerUnit(), commodity.getUpdatedAt());
    }
}
