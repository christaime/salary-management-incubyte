package com.salary.management.application.dto;

import java.math.BigDecimal;

/**
 * Monetary amount with its currency.
 *
 * @param amount   amount value
 * @param currency ISO 4217 currency code
 */
public record AmountDto(BigDecimal amount, String currency) {
}