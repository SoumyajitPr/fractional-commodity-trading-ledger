package com.commodityledger.service;

import com.commodityledger.dto.CreateUserRequest;
import com.commodityledger.dto.MoneyRequest;
import com.commodityledger.dto.TradeRequest;
import com.commodityledger.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ten simultaneous ₹2,000 purchases against a ₹10,000 wallet. Without the wallet row
 * lock several requests could read the same balance and overspend; with it, exactly
 * five succeed and the balance ends at exactly zero.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentTradingTest {

    @Autowired
    private UserService userService;
    @Autowired
    private WalletService walletService;
    @Autowired
    private TradeService tradeService;
    @Autowired
    private PortfolioService portfolioService;
    @Autowired
    private LedgerService ledgerService;

    @Test
    void concurrentBuysNeverOverdrawTheWallet() throws Exception {
        Long userId = userService.createUser(
                new CreateUserRequest("Concurrent User", "concurrent-" + UUID.randomUUID() + "@example.com")).id();
        walletService.deposit(userId, new MoneyRequest(new BigDecimal("10000.00")));

        int attempts = 10;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                startSignal.await();
                try {
                    tradeService.buy(new TradeRequest(userId, "GOLD", null, new BigDecimal("2000"), null));
                    return true;
                } catch (InsufficientFundsException e) {
                    return false;
                }
            }));
        }
        startSignal.countDown();

        int successes = 0;
        for (Future<Boolean> result : results) {
            if (result.get(60, TimeUnit.SECONDS)) {
                successes++;
            }
        }
        pool.shutdown();

        assertThat(successes).isEqualTo(5);
        assertThat(walletService.getWallet(userId).balance()).isEqualByComparingTo("0.00");
        assertThat(portfolioService.getPortfolio(userId).holdings().get(0).quantity()).isEqualByComparingTo("0.8");
        assertThat(tradeService.getTradesForUser(userId)).hasSize(5);
        assertThat(ledgerService.verifyIntegrity().consistent()).isTrue();
    }
}
