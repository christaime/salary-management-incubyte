package com.salary.management.application.dto;

import java.math.BigDecimal;

/**
 * Full breakdown of a monetary amount.
 *
 * @param base          base amount
 * @param variable      variable amount
 * @param allowances    allowances amount
 * @param employerTaxes employer taxes amount
 * @param total         total amount
 * @param currency      ISO 4217 currency code
 */
public record AmountsDto(
        BigDecimal base,
        BigDecimal variable,
        BigDecimal allowances,
        BigDecimal employerTaxes,
        BigDecimal total,
        String currency) {
}