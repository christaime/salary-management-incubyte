package com.salary.management.domain.repository.criteria;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;

import java.util.Set;

/** Criteria for querying departments. */
public final class DepartmentCriteria implements Criteria {

    private static final Set<String> SORTABLE_FIELDS = Set.of("code", "name");

    private final String keyword;
    private final String code;
    private final String sortBy;
    private final SortDirection sortDirection;
    private final int page;
    private final int size;

    private DepartmentCriteria(Builder builder) {
        this.keyword = builder.keyword;
        this.code = builder.code;
        this.sortBy = builder.sortBy;
        this.sortDirection = builder.sortDirection;
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Free-text filter matched against {@code name} and {@code code}, or {@code null}.
     *
     * @return keyword filter
     */
    public String keyword() {
        return keyword;
    }

    /**
     * Exact-match filter on {@code code}, or {@code null}.
     *
     * @return code filter
     */
    public String code() {
        return code;
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
        return "name";
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

    /** Builder for {@link DepartmentCriteria}. */
    public static final class Builder {

        private String keyword;
        private String code;
        private String sortBy;
        private SortDirection sortDirection;
        private int page;
        private int size = 20;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
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

        public DepartmentCriteria build() {
            return new DepartmentCriteria(this);
        }
    }
}