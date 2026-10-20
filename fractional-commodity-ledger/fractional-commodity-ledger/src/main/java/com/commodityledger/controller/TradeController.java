package com.commodityledger.controller;

import com.commodityledger.dto.TradeExecutionResponse;
import com.commodityledger.dto.TradeRequest;
import com.commodityledger.dto.TradeResponse;
import com.commodityledger.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Trades")
public class TradeController {

    private final TradeService tradeService;

    public TradeController(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostMapping("/trades/buy")
    @Operation(summary = "Buy a commodity by quantity or by rupee amount")
    public ResponseEntity<TradeExecutionResponse> buy(@Valid @RequestBody TradeRequest request) {
        return respond(tradeService.buy(request));
    }

    @PostMapping("/trades/sell")
    @Operation(summary = "Sell a commodity by quantity or by rupee amount")
    public ResponseEntity<TradeExecutionResponse> sell(@Valid @RequestBody TradeRequest request) {
        return respond(tradeService.sell(request));
    }

    @GetMapping("/trades/{tradeId}")
    @Operation(summary = "Get a trade with its ledger entries")
    public TradeResponse getTrade(@PathVariable("tradeId") Long tradeId) {
        return tradeService.getTrade(tradeId);
    }

    @GetMapping("/users/{userId}/trades")
    @Operation(summary = "Trade history of a user, newest first")
    public List<TradeResponse> getTradesForUser(@PathVariable("userId") Long userId) {
        return tradeService.getTradesForUser(userId);
    }

    /** 201 for a new trade, 200 when an idempotent retry returns the original trade. */
    private static ResponseEntity<TradeExecutionResponse> respond(TradeExecutionResponse result) {
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
}
