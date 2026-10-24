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
 * the same total converted to the reference currency ({@code referenceAmount}).</p>
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
                .referenceAmount(this.referenceAmount);
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