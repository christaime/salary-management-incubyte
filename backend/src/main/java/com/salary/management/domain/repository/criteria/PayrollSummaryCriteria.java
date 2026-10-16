package com.salary.management.domain.repository.criteria;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.EmploymentType;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Criteria for querying payroll summaries. */
public final class PayrollSummaryCriteria implements Criteria {

    private static final Set<String> SORTABLE_FIELDS = Set.of(
            "status",
            "amounts.total",
            "amounts.currency",
            "payrollPeriod.startDate"
    );

    private final UUID employeeId;
    private final String employeeMatricule;
    private final LocalDate rangeStart;
    private final LocalDate rangeEnd;
    private final Set<UUID> anyCountryIds;
    private final Set<UUID> anyDepartmentIds;
    private final Set<UUID> anyRoleIds;
    private final Set<EmploymentType> anyEmploymentTypes;
    private final String sortBy;
    private final SortDirection sortDirection;
    private final int page;
    private final int size;

    private PayrollSummaryCriteria(Builder builder) {
        this.employeeId = builder.employeeId;
        this.employeeMatricule = builder.employeeMatricule;
        this.rangeStart = builder.rangeStart;
        this.rangeEnd = builder.rangeEnd;
        this.anyCountryIds = builder.anyCountryIds;
        this.anyDepartmentIds = builder.anyDepartmentIds;
        this.anyRoleIds = builder.anyRoleIds;
        this.anyEmploymentTypes = builder.anyEmploymentTypes;
        this.sortBy = builder.sortBy;
        this.sortDirection = builder.sortDirection;
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Employee identifier filter, or {@code null}.
     *
     * @return employee identifier
     */
    public UUID employeeId() {
        return employeeId;
    }

    /**
     * Employee matricule filter, or {@code null}.
     *
     * @return employee matricule
     */
    public String employeeMatricule() {
        return employeeMatricule;
    }

    /**
     * Lower bound (inclusive) on the payroll period start date, or {@code null}.
     *
     * @return range start
     */
    public LocalDate rangeStart() {
        return rangeStart;
    }

    /**
     * Upper bound (inclusive) on the payroll period end date, or {@code null}.
     *
     * @return range end
     */
    public LocalDate rangeEnd() {
        return rangeEnd;
    }

    /**
     * Filter on any of the given assignment country identifiers, or {@code null}.
     *
     * @return country identifiers
     */
    public Set<UUID> anyCountryIds() {
        return anyCountryIds;
    }

    /**
     * Filter on any of the given assignment department identifiers, or {@code null}.
     *
     * @return department identifiers
     */
    public Set<UUID> anyDepartmentIds() {
        return anyDepartmentIds;
    }

    /**
     * Filter on any of the given assignment role identifiers, or {@code null}.
     *
     * @return role identifiers
     */
    public Set<UUID> anyRoleIds() {
        return anyRoleIds;
    }

    /**
     * Filter on any of the given assignment employment types, or {@code null}.
     *
     * @return employment types
     */
    public Set<EmploymentType> anyEmploymentTypes() {
        return anyEmploymentTypes;
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
        return "payrollPeriod.startDate";
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

    /** Builder for {@link PayrollSummaryCriteria}. */
    public static final class Builder {

        private UUID employeeId;
        private String employeeMatricule;
        private LocalDate rangeStart;
        private LocalDate rangeEnd;
        private Set<UUID> anyCountryIds;
        private Set<UUID> anyDepartmentIds;
        private Set<UUID> anyRoleIds;
        private Set<EmploymentType> anyEmploymentTypes;
        private String sortBy;
        private SortDirection sortDirection;
        private int page;
        private int size = 20;

        public Builder employeeId(UUID employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder employeeMatricule(String employeeMatricule) {
            this.employeeMatricule = employeeMatricule;
            return this;
        }

        public Builder rangeStart(LocalDate rangeStart) {
            this.rangeStart = rangeStart;
            return this;
        }

        public Builder rangeEnd(LocalDate rangeEnd) {
            this.rangeEnd = rangeEnd;
            return this;
        }

        public Builder anyCountryIds(Set<UUID> anyCountryIds) {
            this.anyCountryIds = anyCountryIds;
            return this;
        }

        public Builder anyDepartmentIds(Set<UUID> anyDepartmentIds) {
            this.anyDepartmentIds = anyDepartmentIds;
            return this;
        }

        public Builder anyRoleIds(Set<UUID> anyRoleIds) {
            this.anyRoleIds = anyRoleIds;
            return this;
        }

        public Builder anyEmploymentTypes(Set<EmploymentType> anyEmploymentTypes) {
            this.anyEmploymentTypes = anyEmploymentTypes;
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

        public PayrollSummaryCriteria build() {
            return new PayrollSummaryCriteria(this);
        }
    }
}