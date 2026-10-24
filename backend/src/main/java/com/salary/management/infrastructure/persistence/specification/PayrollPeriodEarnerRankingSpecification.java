package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.PayrollPeriodEarnerRankingCriteria;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds JPA specifications for {@link PayrollPeriodEarnerRankingEntity}. */
public final class PayrollPeriodEarnerRankingSpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<PayrollPeriodEarnerRankingEntity> build(
            PayrollPeriodEarnerRankingCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.payrollPeriodId() != null) {
                predicates.add(cb.equal(
                        root.get("payrollPeriodId"), criteria.payrollPeriodId()));
            }

            if (criteria.status() != null) {
                predicates.add(cb.equal(root.get("status"), criteria.status()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}