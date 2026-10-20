package com.commodityledger.service;

import com.commodityledger.dto.CommodityResponse;
import com.commodityledger.dto.UpdatePriceRequest;
import com.commodityledger.entity.Commodity;
import com.commodityledger.exception.ResourceNotFoundException;
import com.commodityledger.repository.CommodityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class CommodityService {

    private final CommodityRepository commodityRepository;

    public CommodityService(CommodityRepository commodityRepository) {
        this.commodityRepository = commodityRepository;
    }

    @Transactional(readOnly = true)
    public List<CommodityResponse> listCommodities() {
        return commodityRepository.findAllByOrderBySymbolAsc().stream().map(CommodityResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CommodityResponse getCommodity(String symbol) {
        return CommodityResponse.from(find(symbol));
    }

    /**
     * Changes the market price. Existing trades and the ledger are unaffected (they are
     * recorded at cost); only portfolio valuation and future trades use the new price.
     */
    @Transactional
    public CommodityResponse updatePrice(String symbol, UpdatePriceRequest request) {
        Commodity commodity = find(symbol);
        commodity.updatePrice(request.pricePerUnit());
        commodityRepository.flush();   // run @PreUpdate so updatedAt in the response is current
        return CommodityResponse.from(commodity);
    }

    private Commodity find(String symbol) {
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return commodityRepository.findBySymbol(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Commodity", normalized));
    }
}
