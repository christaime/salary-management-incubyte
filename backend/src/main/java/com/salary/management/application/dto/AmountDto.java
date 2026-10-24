package com.salary.management.application.dto;

import com.salary.management.domain.model.values.AmountUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Monetary amount with its currency.
 *
 * @param amount   amount value
 * @param currency ISO 4217 currency code
 */
public record AmountDto(BigDecimal amount, String currency) {
    public AmountDto {
        if (amount != null) {
            amount = amount.setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
    }
}