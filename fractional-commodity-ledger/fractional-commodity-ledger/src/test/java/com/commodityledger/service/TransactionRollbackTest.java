package com.commodityledger.service;

import com.commodityledger.dto.CreateUserRequest;
import com.commodityledger.dto.MoneyRequest;
import com.commodityledger.dto.TradeRequest;
import com.commodityledger.entity.LedgerTransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Proves @Transactional atomicity: the ledger write is made to fail AFTER the wallet has
 * been debited, the holding updated and the trade saved. All of it must roll back.
 *
 * Uses its own in-memory database because the mocked ledger skips deposit journals,
 * which would otherwise show up as mismatches in other tests' integrity checks.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:rollbacktest;MODE=PostgreSQL;"
        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1")
class TransactionRollbackTest {

    @MockBean
    private LedgerService ledgerService;

    @Autowired
    private UserService userService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private PortfolioService portfolioService;

    @Test
    void failureWhileWritingTheLedgerRollsBackTheWholeTrade() {
        when(ledgerService.record(eq(LedgerTransactionType.BUY), anyLong(), anyLong(), anyString(), anyList()))
                .thenThrow(new IllegalStateException("Simulated ledger failure"));

        Long userId = userService.createUser(
                new CreateUserRequest("Rollback User", "rollback-" + UUID.randomUUID() + "@example.com")).id();
        walletService.deposit(userId, new MoneyRequest(new BigDecimal("10000.00")));

        assertThatThrownBy(() -> tradeService.buy(
                new TradeRequest(userId, "GOLD", null, new BigDecimal("2000"), null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated ledger failure");

        assertThat(walletService.getWallet(userId).balance()).isEqualByComparingTo("10000.00");
        assertThat(portfolioService.getPortfolio(userId).holdings()).isEmpty();
        assertThat(tradeService.getTradesForUser(userId)).isEmpty();
    }
}
