package com.commodityledger.service;

import com.commodityledger.dto.CreateUserRequest;
import com.commodityledger.dto.LedgerEntryResponse;
import com.commodityledger.dto.MoneyRequest;
import com.commodityledger.dto.PortfolioResponse;
import com.commodityledger.dto.TradeExecutionResponse;
import com.commodityledger.dto.TradeRequest;
import com.commodityledger.dto.UpdatePriceRequest;
import com.commodityledger.entity.EntryDirection;
import com.commodityledger.entity.LedgerAccountType;
import com.commodityledger.exception.DuplicateResourceException;
import com.commodityledger.exception.InsufficientFundsException;
import com.commodityledger.exception.InsufficientHoldingsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end service tests against the real schema (H2 in PostgreSQL mode + Flyway).
 * GOLD keeps its seed price (12,500.00/g) in these tests; PLATINUM is the one whose
 * price is changed.
 */
@SpringBootTest
@ActiveProfiles("test")
class TradeFlowIntegrationTest {

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
    @Autowired
    private CommodityService commodityService;

    @Test
    void buyingByAmountUpdatesWalletHoldingTradeAndLedger() {
        Long userId = newUserWithBalance("10000.00");

        TradeExecutionResponse result = tradeService.buy(new TradeRequest(userId, "GOLD", null, money("2000"), null));

        assertThat(result.trade().quantity()).isEqualByComparingTo("0.16");
        assertThat(result.trade().grossAmount()).isEqualByComparingTo("2000.00");
        assertThat(result.walletBalance()).isEqualByComparingTo("8000.00");
        assertThat(result.holdingQuantity()).isEqualByComparingTo("0.16");

        assertThat(result.trade().ledgerEntries()).hasSize(2);
        assertThat(result.trade().ledgerEntries()).anySatisfy(entry -> assertEntry(entry,
                LedgerAccountType.USER_COMMODITY, EntryDirection.DEBIT, "2000.00"));
        assertThat(result.trade().ledgerEntries()).anySatisfy(entry -> assertEntry(entry,
                LedgerAccountType.USER_CASH, EntryDirection.CREDIT, "2000.00"));

        assertThat(walletService.getWallet(userId).balance()).isEqualByComparingTo("8000.00");
        assertThat(tradeService.getTradesForUser(userId)).hasSize(1);
        assertThat(ledgerService.verifyIntegrity().consistent()).isTrue();
    }

    @Test
    void fractionalPurchaseOf500RupeesOfGold() {
        Long userId = newUserWithBalance("500.00");

        TradeExecutionResponse result = tradeService.buy(new TradeRequest(userId, "gold", null, money("500"), null));

        assertThat(result.trade().quantity()).isEqualByComparingTo("0.04");
        assertThat(result.walletBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void sellingAtAHigherPriceRealisesAProfit() {
        commodityService.updatePrice("PLATINUM", new UpdatePriceRequest(money("3200.00")));
        Long userId = newUserWithBalance("10000.00");
        tradeService.buy(new TradeRequest(userId, "PLATINUM", new BigDecimal("1"), null, null));

        commodityService.updatePrice("PLATINUM", new UpdatePriceRequest(money("4000.00")));
        TradeExecutionResponse sale = tradeService.sell(
                new TradeRequest(userId, "PLATINUM", new BigDecimal("0.5"), null, null));

        assertThat(sale.trade().grossAmount()).isEqualByComparingTo("2000.00");
        assertThat(sale.trade().costBasisReleased()).isEqualByComparingTo("1600.00");
        assertThat(sale.trade().realizedPnl()).isEqualByComparingTo("400.00");
        assertThat(sale.walletBalance()).isEqualByComparingTo("8800.00");   // 10000 - 3200 + 2000
        assertThat(sale.holdingQuantity()).isEqualByComparingTo("0.5");
        assertThat(sale.trade().ledgerEntries()).hasSize(3);
        assertThat(sale.trade().ledgerEntries()).anySatisfy(entry -> assertEntry(entry,
                LedgerAccountType.REALIZED_PNL, EntryDirection.CREDIT, "400.00"));

        PortfolioResponse portfolio = portfolioService.getPortfolio(userId);
        assertThat(portfolio.totalRealizedPnl()).isEqualByComparingTo("400.00");
        assertThat(portfolio.totalCostBasis()).isEqualByComparingTo("1600.00");
        assertThat(portfolio.totalMarketValue()).isEqualByComparingTo("2000.00");
        assertThat(portfolio.totalUnrealizedPnl()).isEqualByComparingTo("400.00");
        assertThat(ledgerService.verifyIntegrity().consistent()).isTrue();
    }

    @Test
    void insufficientFundsRejectsTheTradeAndChangesNothing() {
        Long userId = newUserWithBalance("1000.00");

        assertThatThrownBy(() -> tradeService.buy(new TradeRequest(userId, "GOLD", null, money("2000"), null)))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(walletService.getWallet(userId).balance()).isEqualByComparingTo("1000.00");
        assertThat(portfolioService.getPortfolio(userId).holdings()).isEmpty();
        assertThat(tradeService.getTradesForUser(userId)).isEmpty();
    }

    @Test
    void cannotSellMoreThanIsHeld() {
        Long userId = newUserWithBalance("10000.00");
        tradeService.buy(new TradeRequest(userId, "GOLD", new BigDecimal("0.1"), null, null));

        assertThatThrownBy(() -> tradeService.sell(new TradeRequest(userId, "GOLD", new BigDecimal("0.2"), null, null)))
                .isInstanceOf(InsufficientHoldingsException.class);
        assertThat(portfolioService.getPortfolio(userId).holdings().get(0).quantity()).isEqualByComparingTo("0.1");
    }

    @Test
    void repeatingAClientOrderIdReturnsTheOriginalTradeInsteadOfTradingTwice() {
        Long userId = newUserWithBalance("10000.00");
        TradeRequest request = new TradeRequest(userId, "GOLD", null, money("1000"), "order-123");

        TradeExecutionResponse first = tradeService.buy(request);
        TradeExecutionResponse retry = tradeService.buy(request);

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.trade().tradeId()).isEqualTo(first.trade().tradeId());
        assertThat(walletService.getWallet(userId).balance()).isEqualByComparingTo("9000.00");
        assertThat(tradeService.getTradesForUser(userId)).hasSize(1);

        assertThatThrownBy(() -> tradeService.sell(
                new TradeRequest(userId, "GOLD", null, money("100"), "order-123")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void sellingEverythingClosesThePositionWithNoCostLeftBehind() {
        Long userId = newUserWithBalance("1000.00");
        tradeService.buy(new TradeRequest(userId, "SILVER", null, money("333.33"), null));
        BigDecimal held = portfolioService.getPortfolio(userId).holdings().get(0).quantity();

        tradeService.sell(new TradeRequest(userId, "SILVER", held, null, null));

        assertThat(portfolioService.getPortfolio(userId).holdings()).isEmpty();
        assertThat(ledgerService.verifyIntegrity().consistent()).isTrue();
    }

    private Long newUserWithBalance(String amount) {
        Long userId = userService.createUser(
                new CreateUserRequest("Test User", "user-" + UUID.randomUUID() + "@example.com")).id();
        walletService.deposit(userId, new MoneyRequest(new BigDecimal(amount)));
        return userId;
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    private static void assertEntry(LedgerEntryResponse entry, LedgerAccountType account,
                                    EntryDirection direction, String amount) {
        assertThat(entry.accountType()).isEqualTo(account);
        assertThat(entry.direction()).isEqualTo(direction);
        assertThat(entry.amount()).isEqualByComparingTo(amount);
    }
}
