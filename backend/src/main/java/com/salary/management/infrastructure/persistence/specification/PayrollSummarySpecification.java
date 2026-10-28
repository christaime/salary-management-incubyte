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
     * Builds a specification from the criteria's filters. Returns a conjunction
     * when no filter is set.
     *
     * <p>The date range is applied to {@code payrollPeriod.startDate} on both
     * bounds: a period is in scope when its {@code startDate} falls inside
     * {@code [rangeStart, rangeEnd]}. This replaces the earlier "period fully
     * inside range" semantics.</p>
     *
     * <p>The criteria guarantees that {@code payrollPeriodId} and {@code rangeStart /
     * rangeEnd} are never set at the same time; this method trusts that invariant
     * and adds whichever is present.</p>
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<PayrollSummaryEntity> build(
            PayrollSummaryCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.employeeId() != null) {
                predicates.add(cb.equal(root.get("employee").get("id"),
                        criteria.employeeId()));
            }

            if (criteria.employeeMatricule() != null
                    && !criteria.employeeMatricule().isBlank()) {
                predicates.add(cb.equal(root.get("employee").get("matricule"),
                        criteria.employeeMatricule()));
            }

            if (criteria.payrollPeriodId() != null) {
                predicates.add(cb.equal(root.get("payrollPeriod").get("id"),
                        criteria.payrollPeriodId()));
            } else {
                if (criteria.rangeStart() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(
                            root.get("payrollPeriod").get("startDate"),
                            criteria.rangeStart()));
                }
                if (criteria.rangeEnd() != null) {
                    predicates.add(cb.lessThanOrEqualTo(
                            root.get("payrollPeriod").get("startDate"),
                            criteria.rangeEnd()));
                }
            }

            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.excludeIds() != null && !criteria.excludeIds().isEmpty()) {
                predicates.add(cb.not(root.get("id").in(criteria.excludeIds())));
            }
            addSetFilter(predicates, criteria.anyCountryIds(),
                    () -> root.get("assignmentCountryId"));
            addSetFilter(predicates, criteria.anyDepartmentIds(),
                    () -> root.get("assignmentDepartmentId"));
            addSetFilter(predicates, criteria.anyRoleIds(),
                    () -> root.get("assignmentRoleId"));
            addSetFilter(predicates, criteria.anyEmploymentTypes(),
                    () -> root.get("assignmentEmploymentType"));

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static void addSetFilter(
            List<Predicate> predicates,
            Set<?> values,
            java.util.function.Supplier<jakarta.persistence.criteria.Path<?>> path) {
        if (values != null && !values.isEmpty()) {
            predicates.add(path.get().in(values));
        }
    }
}