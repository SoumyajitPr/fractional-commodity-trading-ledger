package com.commodityledger.exception;

import org.springframework.http.HttpStatus;

import java.math.BigDecimal;

public class InsufficientHoldingsException extends ApiException {

    public InsufficientHoldingsException(String commodity, BigDecimal available, BigDecimal requested) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_HOLDINGS",
                "Insufficient " + commodity + " holdings: available " + available.toPlainString()
                        + ", requested " + requested.toPlainString());
    }
}
