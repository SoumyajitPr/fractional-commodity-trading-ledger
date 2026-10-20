package com.commodityledger.service;

import com.commodityledger.entity.EntryDirection;
import com.commodityledger.entity.LedgerAccountType;

import java.math.BigDecimal;

/** One side of a journal, before it is persisted as a LedgerEntry. */
public record LedgerPosting(
        LedgerAccountType accountType,
        Long userId,
        String commoditySymbol,
        EntryDirection direction,
        BigDecimal amount) {

    public static LedgerPosting debit(LedgerAccountType account, Long userId, String commoditySymbol,
                                      BigDecimal amount) {
        return new LedgerPosting(account, userId, commoditySymbol, EntryDirection.DEBIT, amount);
    }

    public static LedgerPosting credit(LedgerAccountType account, Long userId, String commoditySymbol,
                                       BigDecimal amount) {
        return new LedgerPosting(account, userId, commoditySymbol, EntryDirection.CREDIT, amount);
    }
}
