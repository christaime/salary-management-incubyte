package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds JPA specifications for {@link PayrollSummaryEntity}. */
public final class PayrollSummarySpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<PayrollSummaryEntity> build(PayrollSummaryCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.employeeId() != null) {
                predicates.add(cb.equal(root.get("employee").get("id"), criteria.employeeId()));
            }

            if (criteria.employeeMatricule() != null && !criteria.employeeMatricule().isBlank()) {
                predicates.add(cb.equal(
                        root.get("employee").get("matricule"), criteria.employeeMatricule()));
            }

            if (criteria.rangeStart() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("payrollPeriod").get("startDate"), criteria.rangeStart()));
            }

            if (criteria.rangeEnd() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("payrollPeriod").get("endDate"), criteria.rangeEnd()));
            }

            if (isNotEmpty(criteria.anyCountryIds())) {
                predicates.add(
                        root.get("assignmentCountryId").in(criteria.anyCountryIds()));
            }
            if (isNotEmpty(criteria.anyDepartmentIds())) {
                predicates.add(
                        root.get("assignmentDepartmentId").in(criteria.anyDepartmentIds()));
            }
            if (isNotEmpty(criteria.anyRoleIds())) {
                predicates.add(root.get("assignmentRoleId").in(criteria.anyRoleIds()));
            }
            if (isNotEmpty(criteria.anyEmploymentTypes())) {
                predicates.add(
                        root.get("assignmentEmploymentType").in(criteria.anyEmploymentTypes()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isNotEmpty(Set<?> set) {
        return set != null && !set.isEmpty();
    }
}