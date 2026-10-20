package com.commodityledger.service;

import com.commodityledger.dto.LedgerIntegrityReport;
import com.commodityledger.dto.LedgerTransactionResponse;
import com.commodityledger.entity.EntryDirection;
import com.commodityledger.entity.Holding;
import com.commodityledger.entity.LedgerAccountType;
import com.commodityledger.entity.LedgerEntry;
import com.commodityledger.entity.LedgerTransaction;
import com.commodityledger.entity.LedgerTransactionType;
import com.commodityledger.entity.Wallet;
import com.commodityledger.exception.ResourceNotFoundException;
import com.commodityledger.repository.HoldingRepository;
import com.commodityledger.repository.LedgerEntryRepository;
import com.commodityledger.repository.LedgerTransactionRepository;
import com.commodityledger.repository.UserRepository;
import com.commodityledger.repository.WalletRepository;
import com.commodityledger.util.MoneyMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Double-entry bookkeeping. Every movement of value is written as a journal whose
 * debits equal its credits; a journal that does not balance is never saved.
 */
@Service
public class LedgerService {

    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WalletRepository walletRepository;
    private final HoldingRepository holdingRepository;
    private final UserRepository userRepository;

    public LedgerService(LedgerTransactionRepository ledgerTransactionRepository,
                         LedgerEntryRepository ledgerEntryRepository,
                         WalletRepository walletRepository,
                         HoldingRepository holdingRepository,
                         UserRepository userRepository) {
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.walletRepository = walletRepository;
        this.holdingRepository = holdingRepository;
        this.userRepository = userRepository;
    }

    /**
     * Records a balanced journal. {@code Propagation.MANDATORY}: this must run inside the
     * caller's transaction (the trade or wallet operation), so the ledger and the balances
     * it describes are committed or rolled back together. Calling it without a transaction
     * is a programming error and fails immediately.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerTransaction record(LedgerTransactionType type, Long userId, Long tradeId, String description,
                                   List<LedgerPosting> postings) {
        List<LedgerPosting> effective = new ArrayList<>();
        for (LedgerPosting posting : postings) {
            if (posting.amount() == null || posting.amount().signum() < 0) {
                throw new IllegalArgumentException("Ledger amounts must be non-negative: " + posting);
            }
            if (posting.amount().signum() > 0) {
                effective.add(posting);   // zero-value lines carry no information and are skipped
            }
        }
        if (effective.size() < 2) {
            throw new IllegalStateException("A journal needs at least two non-zero entries");
        }

        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (LedgerPosting posting : effective) {
            if (posting.direction() == EntryDirection.DEBIT) {
                debits = debits.add(posting.amount());
            } else {
                credits = credits.add(posting.amount());
            }
        }
        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException("Unbalanced journal: debits " + debits.toPlainString()
                    + " != credits " + credits.toPlainString());
        }

        LedgerTransaction transaction = new LedgerTransaction(type, userId, tradeId, description);
        for (LedgerPosting posting : effective) {
            transaction.addEntry(new LedgerEntry(posting.accountType(), posting.userId(),
                    posting.commoditySymbol(), posting.direction(), MoneyMath.money(posting.amount())));
        }
        return ledgerTransactionRepository.save(transaction);
    }

    @Transactional(readOnly = true)
    public List<LedgerTransactionResponse> getLedgerForUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return ledgerTransactionRepository.findByUserIdWithEntries(userId).stream()
                .map(LedgerTransactionResponse::from)
                .toList();
    }

    /**
     * Audits the whole ledger:
     * <ol>
     *   <li>every journal balances (debits == credits);</li>
     *   <li>total debits == total credits (trial balance);</li>
     *   <li>every wallet balance equals the net of its USER_CASH account;</li>
     *   <li>every holding's cost basis equals the net of its USER_COMMODITY account.</li>
     * </ol>
     */
    @Transactional(readOnly = true)
    public LedgerIntegrityReport verifyIntegrity() {
        List<String> unbalanced = new ArrayList<>();
        for (Object[] row : ledgerEntryRepository.findUnbalancedTransactions()) {
            unbalanced.add("ledgerTransaction " + toLong(row[0]) + ": debits " + toMoney(row[1]).toPlainString()
                    + " != credits " + toMoney(row[2]).toPlainString());
        }

        BigDecimal totalDebits = MoneyMath.money(BigDecimal.ZERO);
        BigDecimal totalCredits = MoneyMath.money(BigDecimal.ZERO);
        for (Object[] row : ledgerEntryRepository.sumByDirection()) {
            if (EntryDirection.DEBIT.name().equals(String.valueOf(row[0]))) {
                totalDebits = toMoney(row[1]);
            } else {
                totalCredits = toMoney(row[1]);
            }
        }

        Map<String, BigDecimal> netByAccount = new HashMap<>();
        for (Object[] row : ledgerEntryRepository.netBalanceByAccount()) {
            netByAccount.put(accountKey(String.valueOf(row[0]), toLong(row[1]), (String) row[2]), toMoney(row[3]));
        }

        List<String> walletMismatches = new ArrayList<>();
        for (Wallet wallet : walletRepository.findAllWithUser()) {
            Long userId = wallet.getUser().getId();
            BigDecimal ledgerBalance = netByAccount.getOrDefault(
                    accountKey(LedgerAccountType.USER_CASH.name(), userId, null), BigDecimal.ZERO);
            if (wallet.getBalance().compareTo(ledgerBalance) != 0) {
                walletMismatches.add("user " + userId + ": wallet " + wallet.getBalance().toPlainString()
                        + " != ledger " + MoneyMath.money(ledgerBalance).toPlainString());
            }
        }

        List<String> holdingMismatches = new ArrayList<>();
        Set<String> checkedHoldingAccounts = new HashSet<>();
        for (Holding holding : holdingRepository.findAllWithCommodity()) {
            Long userId = holding.getUser().getId();
            String symbol = holding.getCommodity().getSymbol();
            String key = accountKey(LedgerAccountType.USER_COMMODITY.name(), userId, symbol);
            checkedHoldingAccounts.add(key);
            BigDecimal ledgerCost = netByAccount.getOrDefault(key, BigDecimal.ZERO);
            if (holding.getCostBasis().compareTo(ledgerCost) != 0) {
                holdingMismatches.add("user " + userId + " " + symbol + ": cost basis "
                        + holding.getCostBasis().toPlainString() + " != ledger "
                        + MoneyMath.money(ledgerCost).toPlainString());
            }
        }
        netByAccount.forEach((key, net) -> {
            if (key.startsWith(LedgerAccountType.USER_COMMODITY.name() + "|")
                    && !checkedHoldingAccounts.contains(key) && net.signum() != 0) {
                holdingMismatches.add("ledger account " + key + " has balance " + MoneyMath.money(net).toPlainString()
                        + " but no holding exists");
            }
        });

        boolean consistent = unbalanced.isEmpty()
                && walletMismatches.isEmpty()
                && holdingMismatches.isEmpty()
                && totalDebits.compareTo(totalCredits) == 0;

        return new LedgerIntegrityReport(consistent, Instant.now(),
                ledgerTransactionRepository.count(), ledgerEntryRepository.count(),
                totalDebits, totalCredits, unbalanced, walletMismatches, holdingMismatches);
    }

    private static String accountKey(String accountType, Long userId, String commoditySymbol) {
        return accountType + "|" + (userId == null ? "" : userId) + "|" + (commoditySymbol == null ? "" : commoditySymbol);
    }

    private static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static BigDecimal toMoney(Object value) {
        if (value == null) {
            return MoneyMath.money(BigDecimal.ZERO);
        }
        BigDecimal decimal = value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
        return MoneyMath.money(decimal);
    }
}
