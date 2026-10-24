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

import java.util.UUID;

/**
 * JPA entity for a country.
 *
 * <p>Column lengths mirror the domain's {@code @Size} constraints. The identifier is assigned
 * by the domain, so no generation strategy is declared.</p>
 */
@Entity
@Table(
        name = "countries",
        uniqueConstraints = @UniqueConstraint(name = "uk_country_iso_code", columnNames = "iso_code")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** ISO 3166-1 alpha-2 or alpha-3 code; domain allows 2 to 3 characters. */
    @Column(name = "iso_code", nullable = false, length = 3)
    private String isoCode;

    /** Display name; domain allows 2 to 100 characters. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** ISO 4217 currency code; exactly 3 characters. */
    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;
}