package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.UUID;

/**
 * Per-employee payroll outcome for a given payroll period, capturing the
 * amounts and the current lifecycle status.
 */
@Getter
public final class PayrollSummary {

    @NotNull
    private final UUID id;

    @NotNull
    private final UUID employeeId;

    @NotNull
    private final PayrollPeriod payrollPeriod;

    @NotNull
    private final UUID employeePeriodicAssignmentId;

    @NotNull
    private final Amounts amounts;

    @NotNull
    private final PayrollSummaryStatus status;

    private PayrollSummary(Builder builder) {
        this.id = builder.id;
        this.employeeId = builder.employeeId;
        this.payrollPeriod = builder.payrollPeriod;
        this.employeePeriodicAssignmentId = builder.employeePeriodicAssignmentId;
        this.amounts = builder.amounts;
        this.status = builder.status;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PayrollSummary withAmounts(Amounts newAmounts) {
        return toBuilder().amounts(newAmounts).build();
    }

    public PayrollSummary withStatus(PayrollSummaryStatus newStatus) {
        return toBuilder().status(newStatus).build();
    }

    public PayrollSummary withPayrollPeriod(PayrollPeriod newPayrollPeriod) {
        return toBuilder().payrollPeriod(newPayrollPeriod).build();
    }

    public PayrollSummary withEmployeePeriodicAssignmentId(UUID periodicAssignmentId) {
        return toBuilder().employeePeriodicAssignmentId(periodicAssignmentId).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .employeeId(this.employeeId)
                .payrollPeriod(this.payrollPeriod)
                .employeePeriodicAssignmentId(this.employeePeriodicAssignmentId)
                .amounts(this.amounts)
                .status(this.status);
    }


    /** Hand-written builder validating the composed {@code PayrollSummary}. */
    public static final class Builder {

        private UUID id;
        private UUID employeeId;
        private PayrollPeriod payrollPeriod;
        private UUID employeePeriodicAssignmentId;
        private Amounts amounts;
        private PayrollSummaryStatus status;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder employeeId(UUID employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder payrollPeriod(PayrollPeriod payrollPeriod) {
            this.payrollPeriod = payrollPeriod;
            return this;
        }

        public Builder employeePeriodicAssignmentId(UUID employeePeriodicAssignmentId) {
            this.employeePeriodicAssignmentId = employeePeriodicAssignmentId;
            return this;
        }

        public Builder amounts(Amounts amounts) {
            this.amounts = amounts;
            return this;
        }

        public Builder status(PayrollSummaryStatus status) {
            this.status = status;
            return this;
        }

        public PayrollSummary build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            PayrollSummary summary = new PayrollSummary(this);
            DomainValidation.validate(summary);
            return summary;
        }
    }
}