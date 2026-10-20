package com.commodityledger.service;

import com.commodityledger.dto.HoldingResponse;
import com.commodityledger.dto.PortfolioResponse;
import com.commodityledger.entity.Commodity;
import com.commodityledger.entity.Holding;
import com.commodityledger.entity.Wallet;
import com.commodityledger.exception.ResourceNotFoundException;
import com.commodityledger.repository.HoldingRepository;
import com.commodityledger.repository.TradeRepository;
import com.commodityledger.repository.WalletRepository;
import com.commodityledger.util.MoneyMath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Values a user's holdings at current commodity prices. */
@Service
public class PortfolioService {

    private final WalletRepository walletRepository;
    private final HoldingRepository holdingRepository;
    private final TradeRepository tradeRepository;

    public PortfolioService(WalletRepository walletRepository, HoldingRepository holdingRepository,
                            TradeRepository tradeRepository) {
        this.walletRepository = walletRepository;
        this.holdingRepository = holdingRepository;
        this.tradeRepository = tradeRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioResponse getPortfolio(Long userId) {
        Wallet wallet = walletRepository.findByUserIdWithUser(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        List<HoldingResponse> holdings = new ArrayList<>();
        BigDecimal totalCost = MoneyMath.money(BigDecimal.ZERO);
        BigDecimal totalMarketValue = MoneyMath.money(BigDecimal.ZERO);

        for (Holding holding : holdingRepository.findOpenPositions(userId)) {
            Commodity commodity = holding.getCommodity();
            BigDecimal marketValue = MoneyMath.value(holding.getQuantity(), commodity.getPricePerUnit());
            BigDecimal unrealized = marketValue.subtract(holding.getCostBasis());
            holdings.add(new HoldingResponse(
                    commodity.getSymbol(),
                    commodity.getName(),
                    commodity.getUnit(),
                    holding.getQuantity(),
                    MoneyMath.averageCost(holding.getCostBasis(), holding.getQuantity()),
                    holding.getCostBasis(),
                    commodity.getPricePerUnit(),
                    marketValue,
                    unrealized));
            totalCost = totalCost.add(holding.getCostBasis());
            totalMarketValue = totalMarketValue.add(marketValue);
        }

        BigDecimal realized = tradeRepository.sumRealizedPnl(userId);
        BigDecimal totalRealized = MoneyMath.money(realized == null ? BigDecimal.ZERO : realized);

        return new PortfolioResponse(
                userId,
                wallet.getBalance(),
                holdings,
                totalCost,
                totalMarketValue,
                totalMarketValue.subtract(totalCost),
                totalRealized,
                wallet.getBalance().add(totalMarketValue),
                Instant.now());
    }
}
