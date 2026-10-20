package com.commodityledger.dto;

import com.commodityledger.entity.LedgerTransaction;
import com.commodityledger.entity.LedgerTransactionType;

import java.time.Instant;
import java.util.List;

public record LedgerTransactionResponse(
        Long id,
        LedgerTransactionType type,
        Long userId,
        Long tradeId,
        String description,
        Instant createdAt,
        List<LedgerEntryResponse> entries) {

    public static LedgerTransactionResponse from(LedgerTransaction tx) {
        return new LedgerTransactionResponse(tx.getId(), tx.getType(), tx.getUserId(), tx.getTradeId(),
                tx.getDescription(), tx.getCreatedAt(),
                tx.getEntries().stream().map(LedgerEntryResponse::from).toList());
    }
}
