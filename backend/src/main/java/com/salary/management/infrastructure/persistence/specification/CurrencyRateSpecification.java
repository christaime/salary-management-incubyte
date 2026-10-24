package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;
import com.salary.management.infrastructure.persistence.entity.CurrencyRateEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds JPA specifications for {@link CurrencyRateEntity} from {@link CurrencyRateCriteria}. */
public final class CurrencyRateSpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<CurrencyRateEntity> build(CurrencyRateCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("currencyCode")), pattern),
                        cb.like(cb.lower(root.get("referenceCurrencyCode")), pattern)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}