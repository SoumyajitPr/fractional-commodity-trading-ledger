package com.commodityledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdatePriceRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 13, fraction = 2) BigDecimal pricePerUnit) {
}
