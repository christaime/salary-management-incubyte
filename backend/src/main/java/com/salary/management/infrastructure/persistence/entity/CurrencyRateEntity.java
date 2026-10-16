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
 * <p>The identifier is assigned by the domain, so no generation strategy is declared.</p>
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

    /** Source currency code; exactly 3 characters. */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    /** Reference currency code; exactly 3 characters. */
    @Column(name = "reference_currency_code", nullable = false, length = 3)
    private String referenceCurrencyCode;

    /** Exchange rate from source to reference currency; strictly positive in the domain. */
    @Column(name = "rate", nullable = false)
    private BigDecimal rate;
}