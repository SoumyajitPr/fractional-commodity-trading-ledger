package com.commodityledger.controller;

import com.commodityledger.dto.MoneyRequest;
import com.commodityledger.dto.WalletResponse;
import com.commodityledger.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/{userId}/wallet")
@Tag(name = "Wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping
    @Operation(summary = "Get the wallet balance")
    public WalletResponse getWallet(@PathVariable("userId") Long userId) {
        return walletService.getWallet(userId);
    }

    @PostMapping("/deposit")
    @Operation(summary = "Add money to the wallet (ledger: DEBIT USER_CASH / CREDIT EXTERNAL_FUNDING)")
    public WalletResponse deposit(@PathVariable("userId") Long userId, @Valid @RequestBody MoneyRequest request) {
        return walletService.deposit(userId, request);
    }

    @PostMapping("/withdraw")
    @Operation(summary = "Take money out of the wallet (ledger: DEBIT EXTERNAL_FUNDING / CREDIT USER_CASH)")
    public WalletResponse withdraw(@PathVariable("userId") Long userId, @Valid @RequestBody MoneyRequest request) {
        return walletService.withdraw(userId, request);
    }
}
