package com.salary.management.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * JPA entity for a currency exchange rate.
 *
 * <p>A row reads as the sentence
 * {@code referenceAmount referenceCurrencyCode = rate currencyCode}. Today every seeded row
 * has {@code referenceAmount = 1}; the column exists so a future row can use a non-1 unit
 * (e.g. {@code 100 JPY = 0.65 USD}) without a schema change. The identifier is assigned by
 * the domain.</p>
 */
@Entity
@Table(
        name = "currency_rates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_currency_rate_pair",
                columnNames = {"currency_code", "reference_currency_code"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrencyRateEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Source currency code; exactly 3 characters. Right-hand side of the sentence. */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Reference currency code; exactly 3 characters. Left-hand side of the sentence. */
    @Column(name = "reference_currency_code", nullable = false, length = 3)
    private String referenceCurrencyCode;

    /** Quantity of reference currency; strictly positive*/
    @Column(name = "reference_amount", nullable = false)
    private BigDecimal referenceAmount;

    /** Quantity of source currency per {@link #referenceAmount} reference units. */
    @Column(name = "rate", nullable = false)
    private BigDecimal rate;
}