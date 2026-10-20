package com.commodityledger.controller;

import com.commodityledger.dto.CommodityResponse;
import com.commodityledger.dto.UpdatePriceRequest;
import com.commodityledger.service.CommodityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/commodities")
@Tag(name = "Commodities")
public class CommodityController {

    private final CommodityService commodityService;

    public CommodityController(CommodityService commodityService) {
        this.commodityService = commodityService;
    }

    @GetMapping
    @Operation(summary = "List tradable commodities and their current prices")
    public List<CommodityResponse> listCommodities() {
        return commodityService.listCommodities();
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Get one commodity")
    public CommodityResponse getCommodity(@PathVariable("symbol") String symbol) {
        return commodityService.getCommodity(symbol);
    }

    @PutMapping("/{symbol}/price")
    @Operation(summary = "Update the market price (ADMIN only)")
    public CommodityResponse updatePrice(@PathVariable("symbol") String symbol, @Valid @RequestBody UpdatePriceRequest request) {
        return commodityService.updatePrice(symbol, request);
    }
}
