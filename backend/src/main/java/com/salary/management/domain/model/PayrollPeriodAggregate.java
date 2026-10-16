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
    private final UUID payrollPeriodId;

    @NotNull
    private final UUID countryId;

    @NotNull
    private final UUID departmentId;

    @NotNull
    private final UUID roleId;

    @NotNull
    private final Amounts originalAmounts;

    @NotNull
    private final Amounts referenceAmounts;

    @NotNull
    @PositiveOrZero
    private final Integer employeeCount;

    private PayrollPeriodAggregate(Builder builder) {
        this.id = builder.id;
        this.payrollPeriodId = builder.payrollPeriodId;
        this.countryId = builder.countryId;
        this.departmentId = builder.departmentId;
        this.roleId = builder.roleId;
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
                .payrollPeriodId(this.payrollPeriodId)
                .countryId(this.countryId)
                .departmentId(this.departmentId)
                .roleId(this.roleId)
                .originalAmounts(this.originalAmounts)
                .referenceAmounts(this.referenceAmounts)
                .employeeCount(this.employeeCount);
    }

    /** Hand-written builder validating the composed {@code PayrollPeriodAggregate}. */
    public static final class Builder {

        private UUID id;
        private UUID payrollPeriodId;
        private UUID countryId;
        private UUID departmentId;
        private UUID roleId;
        private Amounts originalAmounts;
        private Amounts referenceAmounts;
        private Integer employeeCount;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder payrollPeriodId(UUID payrollPeriodId) {
            this.payrollPeriodId = payrollPeriodId;
            return this;
        }

        public Builder countryId(UUID countryId) {
            this.countryId = countryId;
            return this;
        }

        public Builder departmentId(UUID departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public Builder roleId(UUID roleId) {
            this.roleId = roleId;
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