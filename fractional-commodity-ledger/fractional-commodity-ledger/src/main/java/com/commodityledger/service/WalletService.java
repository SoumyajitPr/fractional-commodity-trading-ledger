package com.commodityledger.service;

import com.commodityledger.dto.MoneyRequest;
import com.commodityledger.dto.WalletResponse;
import com.commodityledger.entity.LedgerAccountType;
import com.commodityledger.entity.LedgerTransactionType;
import com.commodityledger.entity.Wallet;
import com.commodityledger.exception.InsufficientFundsException;
import com.commodityledger.exception.ResourceNotFoundException;
import com.commodityledger.repository.WalletRepository;
import com.commodityledger.util.MoneyMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final LedgerService ledgerService;

    public WalletService(WalletRepository walletRepository, LedgerService ledgerService) {
        this.walletRepository = walletRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(Long userId) {
        return walletRepository.findByUserIdWithUser(userId)
                .map(WalletResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Adds money from outside the platform.
     * Ledger: DEBIT USER_CASH, CREDIT EXTERNAL_FUNDING.
     */
    @Transactional
    public WalletResponse deposit(Long userId, MoneyRequest request) {
        BigDecimal amount = MoneyMath.money(request.amount());
        Wallet wallet = lockWallet(userId);

        wallet.credit(amount);
        ledgerService.record(LedgerTransactionType.DEPOSIT, userId, null,
                "Deposit of INR " + amount.toPlainString(),
                List.of(LedgerPosting.debit(LedgerAccountType.USER_CASH, userId, null, amount),
                        LedgerPosting.credit(LedgerAccountType.EXTERNAL_FUNDING, userId, null, amount)));
        walletRepository.flush();   // write now: runs DB constraints and refreshes updatedAt
        return WalletResponse.from(wallet);
    }

    /**
     * Sends money out of the platform.
     * Ledger: DEBIT EXTERNAL_FUNDING, CREDIT USER_CASH.
     */
    @Transactional
    public WalletResponse withdraw(Long userId, MoneyRequest request) {
        BigDecimal amount = MoneyMath.money(request.amount());
        Wallet wallet = lockWallet(userId);
        if (!wallet.hasAtLeast(amount)) {
            throw new InsufficientFundsException(wallet.getBalance(), amount);
        }

        wallet.debit(amount);
        ledgerService.record(LedgerTransactionType.WITHDRAWAL, userId, null,
                "Withdrawal of INR " + amount.toPlainString(),
                List.of(LedgerPosting.debit(LedgerAccountType.EXTERNAL_FUNDING, userId, null, amount),
                        LedgerPosting.credit(LedgerAccountType.USER_CASH, userId, null, amount)));
        walletRepository.flush();
        return WalletResponse.from(wallet);
    }

    private Wallet lockWallet(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
