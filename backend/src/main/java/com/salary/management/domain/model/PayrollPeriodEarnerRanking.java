package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single entry in the top or bottom earner ranking of a payroll period,
 * scoped to a (country, department, role) combination.
 */
@Getter
public final class PayrollPeriodEarnerRanking {

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
    private final EarnerRankingDirection direction;

    @NotNull
    @Min(1)
    @Max(5)
    private final Integer rank;

    @NotNull
    private final UUID employeeId;

    @NotNull
    @PositiveOrZero
    private final BigDecimal amount;

    private PayrollPeriodEarnerRanking(Builder builder) {
        this.id = builder.id;
        this.payrollPeriodId = builder.payrollPeriodId;
        this.countryId = builder.countryId;
        this.departmentId = builder.departmentId;
        this.roleId = builder.roleId;
        this.direction = builder.direction;
        this.rank = builder.rank;
        this.employeeId = builder.employeeId;
        this.amount = builder.amount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public PayrollPeriodEarnerRanking withRank(int newRank) {
        return toBuilder().rank(newRank).build();
    }

    public PayrollPeriodEarnerRanking withAmount(BigDecimal newAmount) {
        return toBuilder().amount(newAmount).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .payrollPeriodId(this.payrollPeriodId)
                .countryId(this.countryId)
                .departmentId(this.departmentId)
                .roleId(this.roleId)
                .direction(this.direction)
                .rank(this.rank)
                .employeeId(this.employeeId)
                .amount(this.amount);
    }

    /** Hand-written builder validating the composed {@code PayrollPeriodEarnerRanking}. */
    public static final class Builder {

        private UUID id;
        private UUID payrollPeriodId;
        private UUID countryId;
        private UUID departmentId;
        private UUID roleId;
        private EarnerRankingDirection direction;
        private Integer rank;
        private UUID employeeId;
        private BigDecimal amount;

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

        public Builder direction(EarnerRankingDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder rank(Integer rank) {
            this.rank = rank;
            return this;
        }

        public Builder employeeId(UUID employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
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