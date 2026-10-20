package com.commodityledger.dto;

import com.commodityledger.entity.EntryDirection;
import com.commodityledger.entity.LedgerAccountType;
import com.commodityledger.entity.LedgerEntry;

import java.math.BigDecimal;

public record LedgerEntryResponse(
        String account,
        LedgerAccountType accountType,
        EntryDirection direction,
        BigDecimal amount) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(entry.accountCode(), entry.getAccountType(),
                entry.getDirection(), entry.getAmount());
    }
}
