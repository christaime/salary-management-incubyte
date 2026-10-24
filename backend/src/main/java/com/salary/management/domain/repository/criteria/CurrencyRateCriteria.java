package com.salary.management.domain.repository.criteria;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;

import java.util.Set;

/** Criteria for querying currency rates. */
public final class CurrencyRateCriteria implements Criteria {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("currencyCode", "referenceCurrencyCode", "rate");

    private final String keyword;
    private final String sortBy;
    private final SortDirection sortDirection;
    private final int page;
    private final int size;

    private CurrencyRateCriteria(Builder builder) {
        this.keyword = builder.keyword;
        this.sortBy = builder.sortBy;
        this.sortDirection = builder.sortDirection;
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Free-text filter matched against {@code currencyCode} and {@code referenceCurrencyCode},
     * or {@code null}.
     *
     * @return keyword filter
     */
    public String keyword() {
        return keyword;
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
        return "currencyCode";
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

    /** Builder for {@link CurrencyRateCriteria}. */
    public static final class Builder {

        private String keyword;
        private String sortBy;
        private SortDirection sortDirection;
        private int page;
        private int size = 20;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
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

        public CurrencyRateCriteria build() {
            return new CurrencyRateCriteria(this);
        }
    }
}