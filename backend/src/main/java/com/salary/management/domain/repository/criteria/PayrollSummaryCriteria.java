package com.salary.management.domain.repository.criteria;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;

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
    private final UUID payrollPeriodId;
    private final LocalDate rangeStart;
    private final LocalDate rangeEnd;
    private final PayrollSummaryStatus status;
    private final boolean nonExistent;
    private final Set<UUID> anyCountryIds;
    private final Set<UUID> anyDepartmentIds;
    private final Set<UUID> anyRoleIds;
    private final Set<EmploymentType> anyEmploymentTypes;
    private final Set<UUID> excludeIds;
    private final String sortBy;
    private final SortDirection sortDirection;
    private final int page;
    private final int size;

    private PayrollSummaryCriteria(Builder builder) {
        if (builder.nonExistent) {
            if (builder.payrollPeriodId == null) {
                throw new IllegalArgumentException(
                        "nonExistent requires payrollPeriodId");
            }
            if (builder.rangeStart != null || builder.rangeEnd != null) {
                throw new IllegalArgumentException(
                        "nonExistent cannot be combined with rangeStart/rangeEnd");
            }
        } else {
            if (builder.payrollPeriodId != null
                    && (builder.rangeStart != null || builder.rangeEnd != null)) {
                throw new IllegalArgumentException(
                        "payrollPeriodId and rangeStart/rangeEnd are mutually exclusive");
            }
            if (builder.employeeId == null
                    && builder.payrollPeriodId == null
                    && builder.rangeStart == null
                    && builder.rangeEnd == null) {
                throw new IllegalArgumentException(
                        "at least one of employeeId, payrollPeriodId or rangeStart/rangeEnd "
                                + "is required");
            }
        }
        this.employeeId = builder.employeeId;
        this.employeeMatricule = builder.employeeMatricule;
        this.payrollPeriodId = builder.payrollPeriodId;
        this.rangeStart = builder.rangeStart;
        this.rangeEnd = builder.rangeEnd;
        this.status = builder.status;
        this.nonExistent = builder.nonExistent;
        this.anyCountryIds = builder.anyCountryIds;
        this.anyDepartmentIds = builder.anyDepartmentIds;
        this.anyRoleIds = builder.anyRoleIds;
        this.anyEmploymentTypes = builder.anyEmploymentTypes;
        this.excludeIds = builder.excludeIds;
        this.sortBy = builder.sortBy;
        this.sortDirection = builder.sortDirection;
        this.page = builder.page;
        this.size = builder.size;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID employeeId() {
        return employeeId;
    }

    public String employeeMatricule() {
        return employeeMatricule;
    }

    public UUID payrollPeriodId() {
        return payrollPeriodId;
    }

    public LocalDate rangeStart() {
        return rangeStart;
    }

    public LocalDate rangeEnd() {
        return rangeEnd;
    }

    public PayrollSummaryStatus status() {
        return status;
    }

    /**
     * When {@code true}, the query returns payroll summaries that <em>should</em> exist for
     * the period but do not, as synthetic summaries with zero amounts and {@code DRAFT}
     * status. Only {@link #payrollPeriodId()} is used to anchor the period; {@code status} is
     * ignored in this mode.
     */
    public boolean nonExistent() {
        return nonExistent;
    }

    public Set<UUID> anyCountryIds() {
        return anyCountryIds;
    }

    public Set<UUID> anyDepartmentIds() {
        return anyDepartmentIds;
    }

    public Set<UUID> anyRoleIds() {
        return anyRoleIds;
    }

    public Set<EmploymentType> anyEmploymentTypes() {
        return anyEmploymentTypes;
    }

    public Set<UUID> excludeIds() { return excludeIds; }
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
        private UUID payrollPeriodId;
        private LocalDate rangeStart;
        private LocalDate rangeEnd;
        private PayrollSummaryStatus status;
        private boolean nonExistent;
        private Set<UUID> anyCountryIds;
        private Set<UUID> anyDepartmentIds;
        private Set<UUID> anyRoleIds;
        private Set<EmploymentType> anyEmploymentTypes;
        private Set<UUID> excludeIds;
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

        public Builder payrollPeriodId(UUID payrollPeriodId) {
            this.payrollPeriodId = payrollPeriodId;
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

        public Builder status(PayrollSummaryStatus status) {
            this.status = status;
            return this;
        }

        public Builder nonExistent(boolean nonExistent) {
            this.nonExistent = nonExistent;
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

        public Builder excludeIds(Set<UUID> excludeIds) {
            this.excludeIds = excludeIds;
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