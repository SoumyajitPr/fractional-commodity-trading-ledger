package com.commodityledger.dto;

import com.commodityledger.entity.User;
import com.commodityledger.entity.Wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record UserResponse(
        Long id,
        String name,
        String email,
        BigDecimal walletBalance,
        String currency,
        Instant createdAt) {

    public static UserResponse from(Wallet wallet) {
        User user = wallet.getUser();
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                wallet.getBalance(), wallet.getCurrency(), user.getCreatedAt());
    }
}
