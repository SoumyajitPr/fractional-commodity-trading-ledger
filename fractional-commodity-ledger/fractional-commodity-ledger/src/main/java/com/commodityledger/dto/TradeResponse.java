package com.commodityledger.dto;

import com.commodityledger.entity.LedgerTransaction;
import com.commodityledger.entity.Trade;
import com.commodityledger.entity.TradeSide;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TradeResponse(
        Long tradeId,
        Long userId,
        TradeSide side,
        String commodity,
        String unit,
        BigDecimal quantity,
        BigDecimal pricePerUnit,
        BigDecimal grossAmount,
        BigDecimal costBasisReleased,
        BigDecimal realizedPnl,
        String clientOrderId,
        Instant executedAt,
        Long ledgerTransactionId,
        List<LedgerEntryResponse> ledgerEntries) {

    public static TradeResponse from(Trade trade, LedgerTransaction ledgerTx) {
        return new TradeResponse(
                trade.getId(),
                trade.getUser().getId(),
                trade.getSide(),
                trade.getCommodity().getSymbol(),
                trade.getCommodity().getUnit(),
                trade.getQuantity(),
                trade.getPricePerUnit(),
                trade.getGrossAmount(),
                trade.getCostBasisReleased(),
                trade.getRealizedPnl(),
                trade.getClientOrderId(),
                trade.getCreatedAt(),
                ledgerTx == null ? null : ledgerTx.getId(),
                ledgerTx == null ? List.of() : ledgerTx.getEntries().stream().map(LedgerEntryResponse::from).toList());
    }
}
