package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds JPA specifications for {@link EmployeePeriodicAssignmentEntity}. */
public final class EmployeePeriodicAssignmentSpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<EmployeePeriodicAssignmentEntity> build(
            EmployeePeriodicAssignmentCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.employeeId() != null) {
                predicates.add(cb.equal(root.get("employeeId"), criteria.employeeId()));
            }

            if (criteria.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("startDate"), criteria.startDate()));
            }

            if (criteria.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("endDate"), criteria.endDate()));
            }

            if (isNotEmpty(criteria.anyCountryIds())) {
                predicates.add(root.get("countryId").in(criteria.anyCountryIds()));
            }
            if (isNotEmpty(criteria.anyDepartmentIds())) {
                predicates.add(root.get("departmentId").in(criteria.anyDepartmentIds()));
            }
            if (isNotEmpty(criteria.anyRoleIds())) {
                predicates.add(root.get("roleId").in(criteria.anyRoleIds()));
            }
            if (isNotEmpty(criteria.anyEmploymentTypes())) {
                predicates.add(
                        root.get("employmentType").in(criteria.anyEmploymentTypes()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isNotEmpty(Set<?> set) {
        return set != null && !set.isEmpty();
    }
}