package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds JPA specifications for {@link PayrollPeriodEntity} from {@link PayrollPeriodCriteria}. */
public final class PayrollPeriodSpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<PayrollPeriodEntity> build(PayrollPeriodCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("name")), pattern));
            }

            if (criteria.startDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get("startDate"), criteria.startDate()));
            }

            if (criteria.endDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                        root.get("endDate"), criteria.endDate()));
            }

            if (criteria.frequency() != null) {
                predicates.add(cb.equal(root.get("frequency"), criteria.frequency()));
            }

            if (criteria.isClosed() != null) {
                predicates.add(cb.equal(root.get("closed"), criteria.isClosed()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}