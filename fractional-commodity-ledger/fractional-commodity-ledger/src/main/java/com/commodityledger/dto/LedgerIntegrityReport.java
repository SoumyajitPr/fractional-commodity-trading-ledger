package com.commodityledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Result of GET /api/ledger/verify.
 *
 * @param consistent             true when every check below passes
 * @param unbalancedTransactions journals whose debits != credits
 * @param walletMismatches       wallets whose balance != net of their USER_CASH ledger account
 * @param holdingMismatches      holdings whose cost basis != net of their USER_COMMODITY ledger account
 */
public record LedgerIntegrityReport(
        boolean consistent,
        Instant checkedAt,
        long ledgerTransactions,
        long ledgerEntries,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        List<String> unbalancedTransactions,
        List<String> walletMismatches,
        List<String> holdingMismatches) {
}
