package com.commodityledger.service;

import com.commodityledger.dto.TradeExecutionResponse;
import com.commodityledger.dto.TradeRequest;
import com.commodityledger.dto.TradeResponse;
import com.commodityledger.entity.Commodity;
import com.commodityledger.entity.Holding;
import com.commodityledger.entity.LedgerAccountType;
import com.commodityledger.entity.LedgerTransaction;
import com.commodityledger.entity.LedgerTransactionType;
import com.commodityledger.entity.Trade;
import com.commodityledger.entity.TradeSide;
import com.commodityledger.entity.User;
import com.commodityledger.entity.Wallet;
import com.commodityledger.exception.DuplicateResourceException;
import com.commodityledger.exception.InsufficientFundsException;
import com.commodityledger.exception.InsufficientHoldingsException;
import com.commodityledger.exception.InvalidTradeException;
import com.commodityledger.exception.ResourceNotFoundException;
import com.commodityledger.repository.CommodityRepository;
import com.commodityledger.repository.HoldingRepository;
import com.commodityledger.repository.LedgerTransactionRepository;
import com.commodityledger.repository.TradeRepository;
import com.commodityledger.repository.UserRepository;
import com.commodityledger.repository.WalletRepository;
import com.commodityledger.util.MoneyMath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Executes buys and sells. Each trade is ONE database transaction that:
 * <ol>
 *   <li>locks the user's wallet row (serialises concurrent trades of the same user),</li>
 *   <li>validates the request and the user's funds / holdings,</li>
 *   <li>updates the wallet and the holding,</li>
 *   <li>stores the trade,</li>
 *   <li>writes a balanced double-entry journal.</li>
 * </ol>
 * If any step fails, every step is rolled back.
 */
@Service
public class TradeService {

    private static final Logger log = LoggerFactory.getLogger(TradeService.class);

    private final WalletRepository walletRepository;
    private final CommodityRepository commodityRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final UserRepository userRepository;
    private final LedgerService ledgerService;

    public TradeService(WalletRepository walletRepository,
                        CommodityRepository commodityRepository,
                        HoldingRepository holdingRepository,
                        TradeRepository tradeRepository,
                        LedgerTransactionRepository ledgerTransactionRepository,
                        UserRepository userRepository,
                        LedgerService ledgerService) {
        this.walletRepository = walletRepository;
        this.commodityRepository = commodityRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.userRepository = userRepository;
        this.ledgerService = ledgerService;
    }

    /**
     * Buys a commodity by quantity or by rupee amount.
     * Ledger: DEBIT USER_COMMODITY (cost), CREDIT USER_CASH (cost).
     */
    @Transactional
    public TradeExecutionResponse buy(TradeRequest request) {
        Long userId = request.userId();
        Wallet wallet = lockWallet(userId);

        Optional<TradeExecutionResponse> replay = findReplay(request, TradeSide.BUY);
        if (replay.isPresent()) {
            return replay.get();
        }

        Commodity commodity = findCommodity(request.normalizedCommodity());
        BigDecimal price = commodity.getPricePerUnit();
        BigDecimal quantity = resolveQuantity(request, price);
        BigDecimal cost = MoneyMath.value(quantity, price);
        requireNonZeroValue(cost);

        if (!wallet.hasAtLeast(cost)) {
            throw new InsufficientFundsException(wallet.getBalance(), cost);
        }

        User user = wallet.getUser();
        Holding holding = holdingRepository.findForUpdate(userId, commodity.getId())
                .orElseGet(() -> new Holding(user, commodity));

        wallet.debit(cost);
        holding.addPurchase(quantity, cost);
        holdingRepository.save(holding);

        Trade trade = tradeRepository.save(
                Trade.buy(user, commodity, quantity, price, cost, request.clientOrderId()));

        LedgerTransaction journal = ledgerService.record(LedgerTransactionType.BUY, userId, trade.getId(),
                describe(TradeSide.BUY, quantity, commodity, price),
                List.of(LedgerPosting.debit(LedgerAccountType.USER_COMMODITY, userId, commodity.getSymbol(), cost),
                        LedgerPosting.credit(LedgerAccountType.USER_CASH, userId, null, cost)));

        log.info("Trade {}: user {} bought {} {} of {} for INR {}", trade.getId(), userId,
                quantity.toPlainString(), commodity.getUnit(), commodity.getSymbol(), cost.toPlainString());
        return new TradeExecutionResponse(TradeResponse.from(trade, journal), wallet.getBalance(),
                holding.getQuantity(), false);
    }

    /**
     * Sells a commodity by quantity or by rupee amount, using the average-cost method.
     * Ledger: DEBIT USER_CASH (proceeds), CREDIT USER_COMMODITY (cost released),
     * and the difference to REALIZED_PNL (CREDIT for a gain, DEBIT for a loss).
     */
    @Transactional
    public TradeExecutionResponse sell(TradeRequest request) {
        Long userId = request.userId();
        Wallet wallet = lockWallet(userId);

        Optional<TradeExecutionResponse> replay = findReplay(request, TradeSide.SELL);
        if (replay.isPresent()) {
            return replay.get();
        }

        Commodity commodity = findCommodity(request.normalizedCommodity());
        BigDecimal price = commodity.getPricePerUnit();
        BigDecimal quantity = resolveQuantity(request, price);

        Optional<Holding> existing = holdingRepository.findForUpdate(userId, commodity.getId());
        BigDecimal available = existing.map(Holding::getQuantity).orElse(BigDecimal.ZERO);
        if (existing.isEmpty() || available.compareTo(quantity) < 0) {
            throw new InsufficientHoldingsException(commodity.getSymbol(), available, quantity);
        }
        Holding holding = existing.get();

        BigDecimal proceeds = MoneyMath.value(quantity, price);
        requireNonZeroValue(proceeds);
        BigDecimal costReleased = MoneyMath.proportionalCost(holding.getCostBasis(), holding.getQuantity(), quantity);
        BigDecimal realizedPnl = proceeds.subtract(costReleased);

        holding.removeSale(quantity, costReleased);
        wallet.credit(proceeds);

        Trade trade = tradeRepository.save(Trade.sell(wallet.getUser(), commodity, quantity, price, proceeds,
                costReleased, realizedPnl, request.clientOrderId()));

        List<LedgerPosting> postings = new ArrayList<>();
        postings.add(LedgerPosting.debit(LedgerAccountType.USER_CASH, userId, null, proceeds));
        postings.add(LedgerPosting.credit(LedgerAccountType.USER_COMMODITY, userId, commodity.getSymbol(), costReleased));
        if (realizedPnl.signum() > 0) {
            postings.add(LedgerPosting.credit(LedgerAccountType.REALIZED_PNL, userId, null, realizedPnl));
        } else if (realizedPnl.signum() < 0) {
            postings.add(LedgerPosting.debit(LedgerAccountType.REALIZED_PNL, userId, null, realizedPnl.negate()));
        }
        LedgerTransaction journal = ledgerService.record(LedgerTransactionType.SELL, userId, trade.getId(),
                describe(TradeSide.SELL, quantity, commodity, price), postings);

        log.info("Trade {}: user {} sold {} {} of {} for INR {} (realised P&L {})", trade.getId(), userId,
                quantity.toPlainString(), commodity.getUnit(), commodity.getSymbol(), proceeds.toPlainString(),
                realizedPnl.toPlainString());
        return new TradeExecutionResponse(TradeResponse.from(trade, journal), wallet.getBalance(),
                holding.getQuantity(), false);
    }

    @Transactional(readOnly = true)
    public TradeResponse getTrade(Long tradeId) {
        Trade trade = tradeRepository.findByIdWithCommodity(tradeId)
                .orElseThrow(() -> new ResourceNotFoundException("Trade", tradeId));
        LedgerTransaction journal = ledgerTransactionRepository.findByTradeIdWithEntries(tradeId).orElse(null);
        return TradeResponse.from(trade, journal);
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getTradesForUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        List<Trade> trades = tradeRepository.findByUserIdWithCommodity(userId);
        if (trades.isEmpty()) {
            return List.of();
        }
        Map<Long, LedgerTransaction> journalsByTrade = ledgerTransactionRepository
                .findByTradeIdsWithEntries(trades.stream().map(Trade::getId).toList()).stream()
                .collect(Collectors.toMap(LedgerTransaction::getTradeId, Function.identity()));
        return trades.stream()
                .map(trade -> TradeResponse.from(trade, journalsByTrade.get(trade.getId())))
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private Wallet lockWallet(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private Commodity findCommodity(String symbol) {
        return commodityRepository.findBySymbol(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Commodity", symbol));
    }

    /**
     * Idempotency: if this clientOrderId was already executed for the user, return the
     * original trade instead of trading again (safe client retries after a timeout).
     * Runs after the wallet lock, so two identical concurrent requests cannot both trade.
     */
    private Optional<TradeExecutionResponse> findReplay(TradeRequest request, TradeSide side) {
        if (request.clientOrderId() == null) {
            return Optional.empty();
        }
        return tradeRepository.findByUserIdAndClientOrderId(request.userId(), request.clientOrderId())
                .map(previous -> {
                    if (previous.getSide() != side) {
                        throw new DuplicateResourceException("clientOrderId '" + request.clientOrderId()
                                + "' was already used for a " + previous.getSide() + " trade");
                    }
                    LedgerTransaction journal = ledgerTransactionRepository
                            .findByTradeIdWithEntries(previous.getId()).orElse(null);
                    Wallet wallet = walletRepository.findByUserIdWithUser(request.userId()).orElseThrow();
                    BigDecimal holdingQuantity = holdingRepository
                            .findByUserAndCommodity(request.userId(), previous.getCommodity().getId())
                            .map(Holding::getQuantity)
                            .orElse(MoneyMath.quantity(BigDecimal.ZERO));
                    return new TradeExecutionResponse(TradeResponse.from(previous, journal), wallet.getBalance(),
                            holdingQuantity, true);
                });
    }

    /** Quantity as given, or the largest quantity (6 dp, rounded down) that the rupee amount covers. */
    private static BigDecimal resolveQuantity(TradeRequest request, BigDecimal price) {
        if (request.quantity() != null) {
            return MoneyMath.quantity(request.quantity());
        }
        BigDecimal quantity = MoneyMath.quantityForAmount(MoneyMath.money(request.amount()), price);
        if (quantity.signum() == 0) {
            throw new InvalidTradeException("AMOUNT_TOO_SMALL",
                    "INR " + request.amount().toPlainString() + " is too small to trade at INR "
                            + price.toPlainString() + " per unit");
        }
        return quantity;
    }

    private static void requireNonZeroValue(BigDecimal value) {
        if (value.signum() <= 0) {
            throw new InvalidTradeException("TRADE_VALUE_TOO_SMALL",
                    "Trade value rounds to INR 0.00; increase the quantity");
        }
    }

    private static String describe(TradeSide side, BigDecimal quantity, Commodity commodity, BigDecimal price) {
        return side + " " + quantity.toPlainString() + " " + commodity.getUnit() + " " + commodity.getSymbol()
                + " @ INR " + price.toPlainString();
    }
}
