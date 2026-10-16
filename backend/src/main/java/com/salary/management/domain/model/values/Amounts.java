package com.salary.management.domain.model.values;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.common.validation.ValidationError;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable monetary value object describing the composition of a payroll amount.
 *
 * <p>{@code total} is always the sum of {@code base}, {@code variable},
 * {@code allowances} and {@code employerTaxes}. All amounts are non-negative and
 * expressed in a single 3-letter currency.</p>
 */
@Getter
public final class Amounts {

    @NotNull
    @PositiveOrZero
    private final BigDecimal base;

    @NotNull
    @PositiveOrZero
    private final BigDecimal variable;

    @NotNull
    @PositiveOrZero
    private final BigDecimal allowances;

    @NotNull
    @PositiveOrZero
    private final BigDecimal employerTaxes;

    @NotNull
    @PositiveOrZero
    private final BigDecimal total;

    @NotBlank
    @Size(min = 3, max = 3)
    private final String currency;

    private Amounts(Builder builder) {
        this.base = Builder.nullToZero(builder.base);
        this.variable = Builder.nullToZero(builder.variable);
        this.allowances = Builder.nullToZero(builder.allowances);
        this.employerTaxes = Builder.nullToZero(builder.employerTaxes);
        this.total = Builder.nullToZero(builder.total);
        this.currency = builder.currency;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a new {@code Amounts} with the given base, and a recomputed total.
     */
    public Amounts withBase(BigDecimal newBase) {
        return toBuilder().base(newBase).build();
    }

    /**
     * @return a new {@code Amounts} with the given variable, and a recomputed total.
     */
    public Amounts withVariable(BigDecimal newVariable) {
        return toBuilder().variable(newVariable).build();
    }

    /**
     * @return a new {@code Amounts} with the given allowances, and a recomputed total.
     */
    public Amounts withAllowances(BigDecimal newAllowances) {
        return toBuilder().allowances(newAllowances).build();
    }

    /**
     * @return a new {@code Amounts} with the given employer taxes, and a recomputed total.
     */
    public Amounts withEmployerTaxes(BigDecimal newEmployerTaxes) {
        return toBuilder().employerTaxes(newEmployerTaxes).build();
    }

    /**
     * @return a new {@code Amounts} with the given currency.
     */
    public Amounts withCurrency(String newCurrency) {
        return toBuilder().currency(newCurrency).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .base(this.base)
                .variable(this.variable)
                .allowances(this.allowances)
                .employerTaxes(this.employerTaxes)
                .currency(this.currency);
    }

    /**
     * Hand-written builder that validates the composed {@code Amounts} on
     * {@link #build()}, including the cross-field invariant that {@code total}
     * equals the sum of its components.
     */
    public static final class Builder {

        private BigDecimal base;
        private BigDecimal variable;
        private BigDecimal allowances;
        private BigDecimal employerTaxes;
        private BigDecimal total;
        private String currency;

        public Builder base(BigDecimal base) {
            this.base = base;
            return this;
        }

        public Builder variable(BigDecimal variable) {
            this.variable = variable;
            return this;
        }

        public Builder allowances(BigDecimal allowances) {
            this.allowances = allowances;
            return this;
        }

        public Builder employerTaxes(BigDecimal employerTaxes) {
            this.employerTaxes = employerTaxes;
            return this;
        }

        /**
         * Optional explicit total. When omitted, the total is computed as the
         * sum of the four components at build time.
         */
        public Builder total(BigDecimal total) {
            this.total = total;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Amounts build() {
            List<ValidationError> extraErrors = new ArrayList<>();

            boolean anyAmountPresent = base != null
                    || variable != null
                    || allowances != null
                    || employerTaxes != null;

            if (anyAmountPresent) {
                BigDecimal sum = nullToZero(base)
                        .add(nullToZero(variable))
                        .add(nullToZero(allowances))
                        .add(nullToZero(employerTaxes));

                if (total == null) {
                    this.total = sum;
                } else if (total.compareTo(sum) != 0) {
                    extraErrors.add(new ValidationError(
                            "total",
                            "TotalMismatch",
                            "total must equal the sum of the provided amounts",
                            Map.of("expected", sum, "actual", total)
                    ));
                }
            }

            Amounts amounts = new Amounts(this);
            DomainValidation.validate(amounts, extraErrors);
            return amounts;
        }

        private static BigDecimal nullToZero(BigDecimal value) {
            return value != null ? value : BigDecimal.ZERO;
        }
    }
}