package com.commodityledger.repository;

import com.commodityledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Aggregate queries used by the ledger integrity check. Native SQL keeps them
 * readable and identical to what you would run by hand in psql.
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    /** Rows: [ledger_transaction_id, total_debits, total_credits] for journals that do not balance. */
    @Query(value = """
            SELECT ledger_transaction_id,
                   SUM(CASE WHEN direction = 'DEBIT'  THEN amount ELSE 0 END) AS debits,
                   SUM(CASE WHEN direction = 'CREDIT' THEN amount ELSE 0 END) AS credits
            FROM ledger_entries
            GROUP BY ledger_transaction_id
            HAVING SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE 0 - amount END) <> 0
            """, nativeQuery = true)
    List<Object[]> findUnbalancedTransactions();

    /** Rows: [direction, total]. */
    @Query(value = """
            SELECT direction, SUM(amount) AS total
            FROM ledger_entries
            GROUP BY direction
            """, nativeQuery = true)
    List<Object[]> sumByDirection();

    /** Rows: [account_type, user_id, commodity_symbol, net_debit_balance]. */
    @Query(value = """
            SELECT account_type, user_id, commodity_symbol,
                   SUM(CASE WHEN direction = 'DEBIT' THEN amount ELSE 0 - amount END) AS net
            FROM ledger_entries
            GROUP BY account_type, user_id, commodity_symbol
            """, nativeQuery = true)
    List<Object[]> netBalanceByAccount();
}
