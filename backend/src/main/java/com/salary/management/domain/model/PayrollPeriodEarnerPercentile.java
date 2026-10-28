package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amount;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

/**
 * One percentile point (P0, P10, P25, P50, P75, P90, P100) of the earner
 * distribution of a payroll period, scoped to a (country, department, role,
 * employment type) combination.
 *
 * <p>Seven of these rows together form the distribution strip shown in the
 * ranking report. The amount is <b>annualized</b> in the reference currency so
 * that employees paid monthly and bi-weekly are comparable on the same axis.</p>
 *
 * <p>Exactly one of {@code country}, {@code department}, {@code role},
 * {@code employmentType} is non-null for a scoped group; all four are null for
 * the global group.</p>
 */
@Getter
public final class PayrollPeriodEarnerPercentile {

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
    @Min(0)
    @Max(100)
    private final Integer percentile;

    /** Annualized amount in the reference currency. */
    @NotNull
    private final Amount referenceAmount;

    @NotNull
    @Min(1)
    private final Integer employeeCount;

    private PayrollPeriodEarnerPercentile(Builder builder) {
        this.id = builder.id;
        this.payrollPeriod = builder.payrollPeriod;
        this.status = builder.status;
        this.country = builder.country;
        this.department = builder.department;
        this.role = builder.role;
        this.employmentType = builder.employmentType;
        this.percentile = builder.percentile;
        this.referenceAmount = builder.referenceAmount;
        this.employeeCount = builder.employeeCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new percentile entry with the given reference amount.
     *
     * @param newReferenceAmount new annualized reference amount
     * @return new validated percentile entry
     */
    public PayrollPeriodEarnerPercentile withReferenceAmount(Amount newReferenceAmount) {
        return toBuilder().referenceAmount(newReferenceAmount).build();
    }

    /**
     * Returns a new percentile entry with the given population size.
     *
     * @param newEmployeeCount new group size
     * @return new validated percentile entry
     */
    public PayrollPeriodEarnerPercentile withEmployeeCount(Integer newEmployeeCount) {
        return toBuilder().employeeCount(newEmployeeCount).build();
    }

    /**
     * Returns a new percentile entry with the given status.
     *
     * @param newStatus new status
     * @return new validated percentile entry
     */
    public PayrollPeriodEarnerPercentile withStatus(PayrollSummaryStatus newStatus) {
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
                .percentile(this.percentile)
                .referenceAmount(this.referenceAmount)
                .employeeCount(this.employeeCount);
    }

    /** Hand-written builder validating the composed {@code PayrollPeriodEarnerPercentile}. */
    public static final class Builder {

        private UUID id;
        private PayrollPeriod payrollPeriod;
        private PayrollSummaryStatus status;
        private Country country;
        private Department department;
        private Role role;
        private EmploymentType employmentType;
        private Integer percentile;
        private Amount referenceAmount;
        private Integer employeeCount;

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

        public Builder percentile(Integer percentile) {
            this.percentile = percentile;
            return this;
        }

        public Builder referenceAmount(Amount referenceAmount) {
            this.referenceAmount = referenceAmount;
            return this;
        }

        public Builder employeeCount(Integer employeeCount) {
            this.employeeCount = employeeCount;
            return this;
        }

        public PayrollPeriodEarnerPercentile build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            PayrollPeriodEarnerPercentile percentile = new PayrollPeriodEarnerPercentile(this);
            DomainValidation.validate(percentile);
            return percentile;
        }
    }
}