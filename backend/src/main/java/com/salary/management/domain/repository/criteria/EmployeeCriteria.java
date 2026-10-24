package com.salary.management.domain.repository.criteria;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.EmploymentType;

import java.util.Set;
import java.util.UUID;

/** Criteria for querying employees. */
public final class EmployeeCriteria implements Criteria {

    private static final Set<String> SORTABLE_FIELDS =
            Set.of("matricule", "name", "email", "birthDate");

    private final String keyword;
    private final String matricule;
    private final Set<UUID> anyOriginCountryIds;
    private final Set<UUID> anyAssignmentCountryIds;
    private final Set<UUID> anyDepartmentIds;
    private final Set<UUID> anyRoleIds;
    private final Set<EmploymentType> anyEmploymentTypes;
    private final String sortBy;
    private final SortDirection sortDirection;
    private final int page;
    private final int size;

    private EmployeeCriteria(Builder builder) {
        this.keyword = builder.keyword;
        this.matricule = builder.matricule;
        this.anyOriginCountryIds = builder.anyOriginCountryIds;
        this.anyAssignmentCountryIds = builder.anyAssignmentCountryIds;
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
     * Free-text filter matched against {@code matricule}, {@code name}, and {@code email}, or
     * {@code null}.
     *
     * @return keyword filter
     */
    public String keyword() {
        return keyword;
    }

    /**
     * Matricule filter, or {@code null}.
     *
     * @return matricule filter
     */
    public String matricule() {
        return matricule;
    }

    /**
     * Filter on any of the given origin country identifiers, or {@code null}.
     *
     * @return origin country identifiers
     */
    public Set<UUID> anyOriginCountryIds() {
        return anyOriginCountryIds;
    }

    /**
     * Filter on any of the given assignment country identifiers, or {@code null}.
     *
     * @return assignment country identifiers
     */
    public Set<UUID> anyAssignmentCountryIds() {
        return anyAssignmentCountryIds;
    }

    /**
     * Filter on any of the given department identifiers, or {@code null}.
     *
     * @return department identifiers
     */
    public Set<UUID> anyDepartmentIds() {
        return anyDepartmentIds;
    }

    /**
     * Filter on any of the given role identifiers, or {@code null}.
     *
     * @return role identifiers
     */
    public Set<UUID> anyRoleIds() {
        return anyRoleIds;
    }

    /**
     * Filter on any of the given employment types, or {@code null}.
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
        return "matricule";
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

    /** Builder for {@link EmployeeCriteria}. */
    public static final class Builder {

        private String keyword;
        private String matricule;
        private Set<UUID> anyOriginCountryIds;
        private Set<UUID> anyAssignmentCountryIds;
        private Set<UUID> anyDepartmentIds;
        private Set<UUID> anyRoleIds;
        private Set<EmploymentType> anyEmploymentTypes;
        private String sortBy;
        private SortDirection sortDirection;
        private int page;
        private int size = 20;

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder matricule(String matricule) {
            this.matricule = matricule;
            return this;
        }

        public Builder anyOriginCountryIds(Set<UUID> anyOriginCountryIds) {
            this.anyOriginCountryIds = anyOriginCountryIds;
            return this;
        }

        public Builder anyAssignmentCountryIds(Set<UUID> anyAssignmentCountryIds) {
            this.anyAssignmentCountryIds = anyAssignmentCountryIds;
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

        public EmployeeCriteria build() {
            return new EmployeeCriteria(this);
        }
    }
}