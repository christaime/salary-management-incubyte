package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

/**
 * Frozen, immutable view of a payroll period at a given point in time,
 * carrying the period status and any warnings raised during processing.
 */
@Getter
public final class PayrollPeriodSnapshot {

    @NotNull
    private final UUID id;

    @NotNull
    private final UUID payrollPeriodId;

    @NotNull
    private final PayrollSummaryStatus status;

    @NotNull
    private final List<String> warnings;

    private PayrollPeriodSnapshot(Builder builder) {
        this.id = builder.id;
        this.payrollPeriodId = builder.payrollPeriodId;
        this.status = builder.status;
        this.warnings = builder.warnings == null ? List.of() : List.copyOf(builder.warnings);
    }

    public static Builder builder() {
        return new Builder();
    }

    public PayrollPeriodSnapshot withStatus(PayrollSummaryStatus newStatus) {
        return toBuilder().status(newStatus).build();
    }

    public PayrollPeriodSnapshot withWarnings(List<String> newWarnings) {
        return toBuilder().warnings(newWarnings).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .payrollPeriodId(this.payrollPeriodId)
                .status(this.status)
                .warnings(this.warnings);
    }

    /** Hand-written builder validating the composed {@code PayrollPeriodSnapshot}. */
    public static final class Builder {

        private UUID id;
        private UUID payrollPeriodId;
        private PayrollSummaryStatus status;
        private List<String> warnings;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder payrollPeriodId(UUID payrollPeriodId) {
            this.payrollPeriodId = payrollPeriodId;
            return this;
        }

        public Builder status(PayrollSummaryStatus status) {
            this.status = status;
            return this;
        }

        public Builder warnings(List<String> warnings) {
            this.warnings = warnings;
            return this;
        }

        public PayrollPeriodSnapshot build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            PayrollPeriodSnapshot snapshot = new PayrollPeriodSnapshot(this);
            DomainValidation.validate(snapshot);
            return snapshot;
        }
    }
}