package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.domain.model.values.Amounts;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

import java.util.UUID;

/**
 * Aggregated payroll figures for a given period and (country, department, role)
 * combination, expressed both in the original and the reference currency.
 */
@Getter
public final class PayrollPeriodAggregate {

    @NotNull
    private final UUID id;

    @NotNull
    private final PayrollPeriod payrollPeriod;

    @NotNull
    private final Country country;

    @NotNull
    private final Department department;

    @NotNull
    private final Role role;

    @NotNull
    private final Amounts originalAmounts;

    @NotNull
    private final Amounts referenceAmounts;

    @NotNull
    @PositiveOrZero
    private final Integer employeeCount;

    private PayrollPeriodAggregate(Builder builder) {
        this.id = builder.id;
        this.payrollPeriod = builder.payrollPeriod;
        this.country = builder.country;
        this.department = builder.department;
        this.role = builder.role;
        this.originalAmounts = builder.originalAmounts;
        this.referenceAmounts = builder.referenceAmounts;
        this.employeeCount = builder.employeeCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PayrollPeriodAggregate withEmployeeCount(int newEmployeeCount) {
        return toBuilder().employeeCount(newEmployeeCount).build();
    }

    public PayrollPeriodAggregate withReferenceAmounts(Amounts newReferenceAmounts) {
        return toBuilder().referenceAmounts(newReferenceAmounts).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .payrollPeriod(this.payrollPeriod)
                .country(this.country)
                .department(this.department)
                .role(this.role)
                .originalAmounts(this.originalAmounts)
                .referenceAmounts(this.referenceAmounts)
                .employeeCount(this.employeeCount);
    }

    /** Hand-written builder validating the composed {@code PayrollPeriodAggregate}. */
    public static final class Builder {

        private UUID id;
        private PayrollPeriod payrollPeriod;
        private Country country;
        private Department department;
        private Role role;
        private Amounts originalAmounts;
        private Amounts referenceAmounts;
        private Integer employeeCount;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder payrollPeriod(PayrollPeriod payrollPeriod) {
            this.payrollPeriod = payrollPeriod;
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

        public Builder originalAmounts(Amounts originalAmounts) {
            this.originalAmounts = originalAmounts;
            return this;
        }

        public Builder referenceAmounts(Amounts referenceAmounts) {
            this.referenceAmounts = referenceAmounts;
            return this;
        }

        public Builder employeeCount(Integer employeeCount) {
            this.employeeCount = employeeCount;
            return this;
        }

        public PayrollPeriodAggregate build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            PayrollPeriodAggregate aggregate = new PayrollPeriodAggregate(this);
            DomainValidation.validate(aggregate);
            return aggregate;
        }
    }
}