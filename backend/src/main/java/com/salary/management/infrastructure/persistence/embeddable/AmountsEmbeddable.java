package com.salary.management.infrastructure.persistence.embeddable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * JPA embeddable mirroring the domain {@code Amounts} value object.
 *
 * <p>Stored inline in the owning entity. When used more than once in the same entity, column
 * names are overridden via {@code @AttributeOverride}. All monetary components are non-null and
 * default to zero; {@code currency} is required.</p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmountsEmbeddable {

    @Column(name = "base", nullable = false)
    @Builder.Default
    private BigDecimal base = BigDecimal.ZERO;

    @Column(name = "variable", nullable = false)
    @Builder.Default
    private BigDecimal variable = BigDecimal.ZERO;

    @Column(name = "allowances", nullable = false)
    @Builder.Default
    private BigDecimal allowances = BigDecimal.ZERO;

    @Column(name = "employer_taxes", nullable = false)
    @Builder.Default
    private BigDecimal employerTaxes = BigDecimal.ZERO;

    @Column(name = "total", nullable = false)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
}