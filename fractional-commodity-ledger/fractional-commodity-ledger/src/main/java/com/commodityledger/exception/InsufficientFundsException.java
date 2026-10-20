package com.commodityledger.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException(BigDecimal available, BigDecimal required) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS",
                "Insufficient wallet balance: available " + available.toPlainString()
                        + ", required " + required.toPlainString());
    }
}
