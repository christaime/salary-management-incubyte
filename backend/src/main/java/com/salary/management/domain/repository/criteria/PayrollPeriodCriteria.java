package com.salary.management.domain.repository.criteria;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;

import java.time.LocalDate;
import java.util.Set;

/** Criteria for querying payroll periods. */
public final class PayrollPeriodCriteria implements Criteria {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("name", "startDate", "endDate", "frequency", "closed");

    private final String keyword;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final PayrollPeriodFrequency frequency;
    private final Boolean isClosed;
    private final String sortBy;
    private final SortDirection sortDirection;
    private final int page;
    private final int size;

    private PayrollPeriodCriteria(Builder builder) {
        this.keyword = builder.keyword;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.frequency = builder.frequency;
        this.isClosed = builder.isClosed;
        this.sortBy = builder.sortBy;
        this.sortDirection = builder.sortDirection;
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Free-text filter matched against {@code name}, or {@code null}.
     *
     * @return keyword filter
     */
    public String keyword() {
        return keyword;
    }

    /**
     * Lower bound (inclusive) on the period start date, or {@code null}.
     *
     * @return start date filter
     */
    public LocalDate startDate() {
        return startDate;
    }

    /**
     * Upper bound (inclusive) on the period end date, or {@code null}.
     *
     * @return end date filter
     */
    public LocalDate endDate() {
        return endDate;
    }

    /**
     * Frequency filter, or {@code null}.
     *
     * @return frequency filter
     */
    public PayrollPeriodFrequency frequency() {
        return frequency;
    }

    /**
     * Closed flag filter, or {@code null}.
     *
     * @return closed flag filter
     */
    public Boolean isClosed() {
        return isClosed;
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
        return "startDate";
    }

    @Override
    public SortDirection defaultSortDirection() {
        return SortDirection.DESC;
    }

    @Override
    public int page() {
        return page;
    }

    @Override
    public int size() {
        return size;
    }

    /** Builder for {@link PayrollPeriodCriteria}. */
    public static final class Builder {

        private String keyword;
        private LocalDate startDate;
        private LocalDate endDate;
        private PayrollPeriodFrequency frequency;
        private Boolean isClosed;
        private String sortBy;
        private SortDirection sortDirection;
        private int page;
        private int size = 20;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder frequency(PayrollPeriodFrequency frequency) {
            this.frequency = frequency;
            return this;
        }

        public Builder isClosed(Boolean isClosed) {
            this.isClosed = isClosed;
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

        public PayrollPeriodCriteria build() {
            return new PayrollPeriodCriteria(this);
        }
    }
}