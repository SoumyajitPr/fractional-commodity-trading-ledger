package com.commodityledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Buy or sell request. Give EITHER {@code quantity} (units, e.g. grams) OR
 * {@code amount} (rupees, e.g. "invest 500 in gold"), never both.
 */
public record TradeRequest(
        @NotNull @Positive
        Long userId,

        @NotBlank @Size(max = 16)
        @Schema(example = "GOLD")
        String commodity,

        @Positive @Digits(integer = 14, fraction = 6)
        @Schema(description = "Units to trade, up to 6 decimals", example = "0.5")
        BigDecimal quantity,

        @Positive @Digits(integer = 15, fraction = 2)
        @Schema(description = "Rupee amount to trade, up to 2 decimals", example = "500")
        BigDecimal amount,

        @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._:-]+")
        @Schema(description = "Optional idempotency key; repeating it returns the original trade",
                example = "order-0001")
        String clientOrderId) {

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Provide exactly one of 'quantity' or 'amount'")
    public boolean isExactlyOneOfQuantityOrAmount() {
        return (quantity == null) != (amount == null);
    }

    public String normalizedCommodity() {
        return commodity.trim().toUpperCase(Locale.ROOT);
    }
}
