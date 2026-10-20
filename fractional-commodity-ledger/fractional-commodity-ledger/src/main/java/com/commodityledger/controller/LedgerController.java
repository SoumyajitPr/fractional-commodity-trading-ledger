package com.commodityledger.controller;

import com.commodityledger.dto.LedgerIntegrityReport;
import com.commodityledger.dto.LedgerTransactionResponse;
import com.commodityledger.service.LedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/users/{userId}/ledger")
    @Operation(summary = "All journals (deposits, withdrawals, buys, sells) of a user, newest first")
    public List<LedgerTransactionResponse> getLedgerForUser(@PathVariable("userId") Long userId) {
        return ledgerService.getLedgerForUser(userId);
    }

    @GetMapping("/ledger/verify")
    @Operation(summary = "Audit the ledger: balanced journals, trial balance, wallet and holding reconciliation (ADMIN only)")
    public LedgerIntegrityReport verify() {
        return ledgerService.verifyIntegrity();
    }
}
