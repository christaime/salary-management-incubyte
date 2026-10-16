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
    private final PayrollPeriod payrollPeriod;

    @NotNull
    private final Country country;

    @NotNull
    private final Department department;

    @NotNull
    private final Role role;

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
        this.payrollPeriod = builder.payrollPeriod;
        this.country = builder.country;
        this.department = builder.department;
        this.role = builder.role;
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
                .payrollPeriod(this.payrollPeriod)
                .country(this.country)
                .department(this.department)
                .role(this.role)
                .direction(this.direction)
                .rank(this.rank)
                .employeeId(this.employeeId)
                .amount(this.amount);
    }

    /** Hand-written builder validating the composed {@code PayrollPeriodEarnerRanking}. */
    public static final class Builder {

        private UUID id;
        private PayrollPeriod payrollPeriod;
        private Country country;
        private Department department;
        private Role role;
        private EarnerRankingDirection direction;
        private Integer rank;
        private UUID employeeId;
        private BigDecimal amount;

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