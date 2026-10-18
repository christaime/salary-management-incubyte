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
 * JPA embeddable mirroring the domain {@link com.salary.management.domain.model.values.Amount}.
 *
 * <p>Stored inline in the owning entity. When used more than once in the same entity,
 * column names are overridden via {@code @AttributeOverride}.</p>
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmountEmbeddable {

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
}