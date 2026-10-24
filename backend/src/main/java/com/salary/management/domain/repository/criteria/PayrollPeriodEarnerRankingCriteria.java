package com.salary.management.domain.repository.criteria;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;

import java.util.Set;
import java.util.UUID;

/** Criteria for querying payroll period earner rankings. */
public final class PayrollPeriodEarnerRankingCriteria implements Criteria {

    private static final Set<String> SORTABLE_FIELDS = Set.of("rank", "amount");

    private final UUID payrollPeriodId;
    private final PayrollSummaryStatus status;
    private final String sortBy;
    private final SortDirection sortDirection;
    private final int page;
    private final int size;

    private PayrollPeriodEarnerRankingCriteria(Builder builder) {
        this.payrollPeriodId = builder.payrollPeriodId;
        this.status = builder.status;
        this.sortBy = builder.sortBy;
        this.sortDirection = builder.sortDirection;
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Payroll period identifier filter, or {@code null}.
     *
     * @return payroll period identifier
     */
    public UUID payrollPeriodId() {
        return payrollPeriodId;
    }

    /**
     * Status filter, or {@code null}.
     *
     * @return status filter
     */
    public PayrollSummaryStatus status() {
        return status;
    }

    @Override
    public String sortBy() {
        return sortBy;
    }

    @Override
    public SortDirection sortDirection() {
        return sortDirection;
    }

    @Override
    public Set<String> sortableFields() {
        return SORTABLE_FIELDS;
    }

    @Override
    public String defaultSortField() {
        return "rank";
    }

    @Override
    public SortDirection defaultSortDirection() {
        return SortDirection.ASC;
    }

    @Override
    public int page() {
        return page;
    }

    @Override
    public int size() {
        return size;
    }

    /** Builder for {@link PayrollPeriodEarnerRankingCriteria}. */
    public static final class Builder {

        private UUID payrollPeriodId;
        private PayrollSummaryStatus status;
        private String sortBy;
        private SortDirection sortDirection;
        private int page;
        private int size = 20;

        public Builder payrollPeriodId(UUID payrollPeriodId) {
            this.payrollPeriodId = payrollPeriodId;
            return this;
        }

        public Builder status(PayrollSummaryStatus status) {
            this.status = status;
            return this;
        }

        public Builder sortBy(String sortBy) {
            this.sortBy = sortBy;
            return this;
        }

        public Builder sortDirection(SortDirection sortDirection) {
            this.sortDirection = sortDirection;
            return this;
        }

        public Builder page(int page) {
            this.page = page;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public PayrollPeriodEarnerRankingCriteria build() {
            return new PayrollPeriodEarnerRankingCriteria(this);
        }
    }
}