package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

/**
 * One entry in the top or bottom earner ranking of a payroll period, scoped to a
 * (country, department, role, employment type) combination.
 *
 * <p>Carries the employee's total in the assignment currency ({@code originalAmount}) and
 * the same total converted to the reference currency ({@code referenceAmount}). Also
 * carries the labor days the total covers ({@code daysCovered}) and the derived
 * monthly rate ({@code monthlyRateAmount}) that ranking is actually based on — so the
 * HR-facing card can show the reasoning, not just the rank.</p>
 */
@Getter
public final class PayrollPeriodEarnerRanking {

    @NotNull
    private final UUID id;

    @NotNull
    private final PayrollPeriod payrollPeriod;

    @NotNull
    private final PayrollSummaryStatus status;

    private final Country country;

    private final Department department;

    private final Role role;

    private final EmploymentType employmentType;

    @NotNull
    private final EarnerRankingDirection direction;

    @NotNull
    @Min(1)
    @Max(5)
    private final Integer rank;

    @NotNull
    private final Employee employee;

    @NotNull
    private final Amount originalAmount;

    @NotNull
    private final Amount referenceAmount;

    /**
     * Number of labor days the {@code referenceAmount} covers in this period.
     * Examples: 14 (one bi-weekly check), 28 (two bi-weekly checks), 31 (monthly).
     * Used to derive {@link #monthlyRateAmount}, and shown in the UI so the
     * derivation is auditable.
     */
    @NotNull
    @Min(1)
    private final Integer daysCovered;

    /**
     * Monthly rate used as the ranking key:
     * {@code (referenceAmount.amount / daysCovered) × daysInMonth}.
     *
     * <p>Stored explicitly (rather than re-derived on the fly) so that the stored
     * rank and the stored monthly rate can never drift apart. The UI displays this
     * number, and HR reads it as "this is why the employee ranks where they do".</p>
     */
    @NotNull
    private final Amount monthlyRateAmount;

    private PayrollPeriodEarnerRanking(Builder builder) {
        this.id = builder.id;
        this.payrollPeriod = builder.payrollPeriod;
        this.status = builder.status;
        this.country = builder.country;
        this.department = builder.department;
        this.role = builder.role;
        this.employmentType = builder.employmentType;
        this.direction = builder.direction;
        this.rank = builder.rank;
        this.employee = builder.employee;
        this.originalAmount = builder.originalAmount;
        this.referenceAmount = builder.referenceAmount;
        this.daysCovered = builder.daysCovered;
        this.monthlyRateAmount = builder.monthlyRateAmount;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new ranking entry with the given original amount.
     *
     * @param newOriginalAmount new original amount
     * @return new validated ranking entry
     */
    public PayrollPeriodEarnerRanking withOriginalAmount(Amount newOriginalAmount) {
        return toBuilder().originalAmount(newOriginalAmount).build();
    }

    /**
     * Returns a new ranking entry with the given reference amount.
     *
     * @param newReferenceAmount new reference amount
     * @return new validated ranking entry
     */
    public PayrollPeriodEarnerRanking withReferenceAmount(Amount newReferenceAmount) {
        return toBuilder().referenceAmount(newReferenceAmount).build();
    }

    /**
     * Returns a new ranking entry with the given status.
     *
     * @param newStatus new status
     * @return new validated ranking entry
     */
    public PayrollPeriodEarnerRanking withStatus(PayrollSummaryStatus newStatus) {
        return toBuilder().status(newStatus).build();
    }

    /**
     * Returns a new ranking entry with the given labor-day coverage.
     *
     * <p>Note: changing {@code daysCovered} without also changing
     * {@code monthlyRateAmount} will make the two inconsistent. Use
     * {@link #withMonthlyRate(Amount, int)} when both change together.</p>
     *
     * @param newDaysCovered new labor-day coverage
     * @return new validated ranking entry
     */
    public PayrollPeriodEarnerRanking withDaysCovered(Integer newDaysCovered) {
        return toBuilder().daysCovered(newDaysCovered).build();
    }

    /**
     * Returns a new ranking entry with the given monthly rate.
     *
     * @param newMonthlyRateAmount new monthly rate
     * @return new validated ranking entry
     */
    public PayrollPeriodEarnerRanking withMonthlyRate(Amount newMonthlyRateAmount) {
        return toBuilder().monthlyRateAmount(newMonthlyRateAmount).build();
    }

    /**
     * Returns a new ranking entry with the given labor-day coverage and monthly
     * rate, keeping the two consistent.
     *
     * @param newMonthlyRateAmount new monthly rate
     * @param newDaysCovered       new labor-day coverage
     * @return new validated ranking entry
     */
    public PayrollPeriodEarnerRanking withMonthlyRate(Amount newMonthlyRateAmount,
                                                      int newDaysCovered) {
        return toBuilder()
                .monthlyRateAmount(newMonthlyRateAmount)
                .daysCovered(newDaysCovered)
                .build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .payrollPeriod(this.payrollPeriod)
                .status(this.status)
                .country(this.country)
                .department(this.department)
                .role(this.role)
                .employmentType(this.employmentType)
                .direction(this.direction)
                .rank(this.rank)
                .employee(this.employee)
                .originalAmount(this.originalAmount)
                .referenceAmount(this.referenceAmount)
                .daysCovered(this.daysCovered)
                .monthlyRateAmount(this.monthlyRateAmount);
    }

    /** Hand-written builder validating the composed {@code PayrollPeriodEarnerRanking}. */
    public static final class Builder {

        private UUID id;
        private PayrollPeriod payrollPeriod;
        private PayrollSummaryStatus status;
        private Country country;
        private Department department;
        private Role role;
        private EmploymentType employmentType;
        private EarnerRankingDirection direction;
        private Integer rank;
        private Employee employee;
        private Amount originalAmount;
        private Amount referenceAmount;
        private Integer daysCovered;
        private Amount monthlyRateAmount;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder payrollPeriod(PayrollPeriod payrollPeriod) {
            this.payrollPeriod = payrollPeriod;
            return this;
        }

        public Builder status(PayrollSummaryStatus status) {
            this.status = status;
            return this;
        }

        public Builder country(Country country) {
            this.country = country;
            return this;
        }

        public Builder department(Department department) {
            this.department = department;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder employmentType(EmploymentType employmentType) {
            this.employmentType = employmentType;
            return this;
        }

        public Builder direction(EarnerRankingDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder rank(Integer rank) {
            this.rank = rank;
            return this;
        }

        public Builder employee(Employee employee) {
            this.employee = employee;
            return this;
        }

        public Builder originalAmount(Amount originalAmount) {
            this.originalAmount = originalAmount;
            return this;
        }

        public Builder referenceAmount(Amount referenceAmount) {
            this.referenceAmount = referenceAmount;
            return this;
        }

        public Builder daysCovered(Integer daysCovered) {
            this.daysCovered = daysCovered;
            return this;
        }

        public Builder monthlyRateAmount(Amount monthlyRateAmount) {
            this.monthlyRateAmount = monthlyRateAmount;
            return this;
        }

        public PayrollPeriodEarnerRanking build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            PayrollPeriodEarnerRanking ranking = new PayrollPeriodEarnerRanking(this);
            DomainValidation.validate(ranking);
            return ranking;
        }
    }
}