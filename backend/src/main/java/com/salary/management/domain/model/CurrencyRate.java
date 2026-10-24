package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Exchange rate between two currencies, written as a sentence:
 * {@code referenceAmount referenceCurrencyCode = rate currencyCode}.
 *
 * <p>Each field is one part of that sentence: {@code referenceCurrencyCode} is the left-hand
 * currency, {@code referenceAmount} is its quantity, {@code rate} is the right-hand quantity,
 * and {@code currencyCode} is the right-hand currency. Example:
 * {@code (referenceAmount=1, referenceCurrencyCode="USD", rate=583.51, currencyCode="XAF")}
 * reads <em>"1 USD = 583.51 XAF"</em>.</p>
 *
 * <p>The system has a single reference currency: at any moment, every row in
 * {@code currency_rates} carries the same {@code referenceCurrencyCode}. It is an invariant
 * of the table, not a per-row choice, and this class only describes one row at a time.
 * {@code referenceAmount} is always {@code 1} in practice, but it is a real field, not an
 * assumption, so a row such as
 * {@code (referenceAmount=100, referenceCurrencyCode="JPY", rate=0.65, currencyCode="USD")}
 * is valid and reads <em>"100 JPY = 0.65 USD"</em>.</p>
 *
 * <p>Conversion between the two currencies divides by {@code referenceAmount}:
 * {@code amountInCurrencyCode = amountInReferenceCurrencyCode * rate / referenceAmount} and
 * {@code amountInReferenceCurrencyCode = amountInCurrencyCode * referenceAmount / rate}.</p>
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

    /** Quantity of {@link #referenceCurrencyCode} on the left-hand side of the sentence. */
    @NotNull
    @Positive
    private final BigDecimal referenceAmount;

    /** Quantity of {@link #currencyCode} on the right-hand side of the sentence. */
    @NotNull
    @Positive
    private final BigDecimal rate;

    private CurrencyRate(Builder builder) {
        this.id = builder.id;
        this.currencyCode = builder.currencyCode;
        this.referenceCurrencyCode = builder.referenceCurrencyCode;
        this.referenceAmount = builder.referenceAmount;
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
                .referenceAmount(this.referenceAmount)
                .rate(this.rate);
    }

    /** Hand-written builder validating the composed {@code CurrencyRate}. */
    public static final class Builder {

        private UUID id;
        private String currencyCode;
        private String referenceCurrencyCode;
        private BigDecimal referenceAmount;
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

        public Builder referenceAmount(BigDecimal referenceAmount) {
            this.referenceAmount = referenceAmount;
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
            if (this.referenceAmount == null) {
                this.referenceAmount = BigDecimal.ONE;
            }
            List<ValidationError> extraErrors = new ArrayList<>();
            if (this.currencyCode != null && this.currencyCode.equals(this.referenceCurrencyCode) && (!BigDecimal.ONE.equals(this.referenceAmount) || !BigDecimal.ONE.equals(this.rate))) {
                extraErrors.add(new ValidationError(
                        "currencyCode",
                        "InvalidCurrencyRate",
                        "currency_rates self-pair must have reference_amount = 1 and rate = 1",
                        Map.of()
                ));
            }
            CurrencyRate currencyRate = new CurrencyRate(this);
            DomainValidation.validate(currencyRate, extraErrors);
            return currencyRate;
        }
    }
}