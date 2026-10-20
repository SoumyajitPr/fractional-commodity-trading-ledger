package com.commodityledger.dto;

import com.commodityledger.entity.Wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(
        Long userId,
        BigDecimal balance,
        String currency,
        Instant updatedAt) {

    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(wallet.getUser().getId(), wallet.getBalance(),
                wallet.getCurrency(), wallet.getUpdatedAt());
    }
}
