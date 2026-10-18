package com.salary.management.domain.model.values;

import com.salary.management.common.validation.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A single monetary amount with its currency.
 *
 * <p>Unlike {@link Amounts}, which carries the base, variable, allowances, employer taxes,
 * and total of a payroll line, {@code Amount} is a single value. It is used where only a
 * total is meaningful — for example an earner ranking entry, which stores one number per
 * employee and per currency.</p>
 */
@Getter
public final class Amount {

    @NotNull
    private final BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private final String currency;

    private Amount(Builder builder) {
        this.amount = AmountUtils.nullToZero(builder.amount).setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP);
        this.currency = builder.currency;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new amount with the given value.
     *
     * @param newAmount new value
     * @return new validated amount
     */
    public Amount withAmount(BigDecimal newAmount) {
        return toBuilder().amount(newAmount).build();
    }

    /**
     * Returns a new amount with the given currency.
     *
     * @param newCurrency new currency code
     * @return new validated amount
     */
    public Amount withCurrency(String newCurrency) {
        return toBuilder().currency(newCurrency).build();
    }

    private Builder toBuilder() {
        return new Builder().amount(this.amount).currency(this.currency);
    }

    /** Hand-written builder validating the composed {@code Amount}. */
    public static final class Builder {

        private BigDecimal amount;
        private String currency;

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Amount build() {
            Amount amount = new Amount(this);
            DomainValidation.validate(amount);
            return amount;
        }
    }
}