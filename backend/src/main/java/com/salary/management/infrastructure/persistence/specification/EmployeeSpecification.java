package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Builds JPA specifications for {@link EmployeeEntity} from {@link EmployeeCriteria}. */
public final class EmployeeSpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<EmployeeEntity> build(EmployeeCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("matricule")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)
                ));
            }

            if (criteria.matricule() != null && !criteria.matricule().isBlank()) {
                predicates.add(cb.equal(root.get("matricule"), criteria.matricule()));
            }

            if (isNotEmpty(criteria.anyOriginCountryIds())) {
                predicates.add(root.get("originCountryId").in(criteria.anyOriginCountryIds()));
            }

            addAssignmentPredicate(root, query, cb, predicates, criteria);

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Restricts the result to employees that have at least one in-effect assignment matching
     * all supplied assignment filters.
     */
    private static void addAssignmentPredicate(
            Root<EmployeeEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            List<Predicate> predicates,
            EmployeeCriteria criteria) {

        if (!hasAssignmentFilter(criteria)) {
            return;
        }

        Subquery<UUID> subquery = query.subquery(UUID.class);
        Root<EmployeePeriodicAssignmentEntity> assignment =
                subquery.from(EmployeePeriodicAssignmentEntity.class);
        subquery.select(assignment.get("employeeId"));

        List<Predicate> assignmentPredicates = new ArrayList<>();
        assignmentPredicates.add(cb.isTrue(assignment.get("inEffect")));

        if (isNotEmpty(criteria.anyAssignmentCountryIds())) {
            assignmentPredicates.add(
                    assignment.get("countryId").in(criteria.anyAssignmentCountryIds()));
        }
        if (isNotEmpty(criteria.anyDepartmentIds())) {
            assignmentPredicates.add(
                    assignment.get("departmentId").in(criteria.anyDepartmentIds()));
        }
        if (isNotEmpty(criteria.anyRoleIds())) {
            assignmentPredicates.add(
                    assignment.get("roleId").in(criteria.anyRoleIds()));
        }
        if (isNotEmpty(criteria.anyEmploymentTypes())) {
            assignmentPredicates.add(
                    assignment.get("employmentType").in(criteria.anyEmploymentTypes()));
        }

        subquery.where(cb.and(assignmentPredicates.toArray(new Predicate[0])));
        predicates.add(root.get("id").in(subquery));
    }

    private static boolean hasAssignmentFilter(EmployeeCriteria criteria) {
        return isNotEmpty(criteria.anyAssignmentCountryIds())
                || isNotEmpty(criteria.anyDepartmentIds())
                || isNotEmpty(criteria.anyRoleIds())
                || isNotEmpty(criteria.anyEmploymentTypes());
    }

    private static boolean isNotEmpty(Set<?> set) {
        return set != null && !set.isEmpty();
    }
}