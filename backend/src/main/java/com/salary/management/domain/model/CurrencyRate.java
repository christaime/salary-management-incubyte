package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exchange rate from a source currency to a reference currency.
 */
@Getter
public final class CurrencyRate {

    @NotNull
    private final UUID id;

    @NotBlank
    @Size(min = 3, max = 3)
    private final String currencyCode;

    @NotBlank
    @Size(min = 3, max = 3)
    private final String referenceCurrencyCode;

    @NotNull
    @Positive
    private final BigDecimal rate;

    private CurrencyRate(Builder builder) {
        this.id = builder.id;
        this.currencyCode = builder.currencyCode;
        this.referenceCurrencyCode = builder.referenceCurrencyCode;
        this.rate = builder.rate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public CurrencyRate withRate(BigDecimal newRate) {
        return toBuilder().rate(newRate).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .currencyCode(this.currencyCode)
                .referenceCurrencyCode(this.referenceCurrencyCode)
                .rate(this.rate);
    }

    /** Hand-written builder validating the composed {@code CurrencyRate}. */
    public static final class Builder {

        private UUID id;
        private String currencyCode;
        private String referenceCurrencyCode;
        private BigDecimal rate;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Builder referenceCurrencyCode(String referenceCurrencyCode) {
            this.referenceCurrencyCode = referenceCurrencyCode;
            return this;
        }

        public Builder rate(BigDecimal rate) {
            this.rate = rate;
            return this;
        }

        public CurrencyRate build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            CurrencyRate currencyRate = new CurrencyRate(this);
            DomainValidation.validate(currencyRate);
            return currencyRate;
        }
    }
}