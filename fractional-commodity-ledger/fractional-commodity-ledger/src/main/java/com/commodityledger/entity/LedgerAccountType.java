package com.commodityledger.entity;

/**
 * Chart of accounts. Asset accounts (USER_CASH, USER_COMMODITY) increase with a DEBIT;
 * EXTERNAL_FUNDING and REALIZED_PNL are the balancing (equity-side) accounts.
 */
public enum LedgerAccountType {
    /** The user's rupee wallet. Net debit balance == wallets.balance. */
    USER_CASH,
    /** The user's commodity position at cost. Net debit balance == holdings.cost_basis. */
    USER_COMMODITY,
    /** Money entering/leaving the platform (bank deposits and withdrawals). */
    EXTERNAL_FUNDING,
    /** Gains (credit) and losses (debit) realised when a commodity is sold. */
    REALIZED_PNL
}
