package com.commodityledger.controller;

import com.commodityledger.dto.PortfolioResponse;
import com.commodityledger.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Portfolio")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/api/users/{userId}/portfolio")
    @Operation(summary = "Holdings valued at current prices, with realised and unrealised P&L")
    public PortfolioResponse getPortfolio(@PathVariable("userId") Long userId) {
        return portfolioService.getPortfolio(userId);
    }
}
