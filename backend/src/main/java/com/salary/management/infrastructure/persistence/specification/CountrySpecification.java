package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.CountryCriteria;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import org.springframework.data.jpa.domain.Specification;

/** Builds JPA specifications for {@link CountryEntity} from {@link CountryCriteria}. */
public final class CountrySpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<CountryEntity> build(CountryCriteria criteria) {
        return (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("isoCode")), pattern),
                        cb.like(cb.lower(root.get("name")), pattern)
                ));
            }

            if (criteria.currencyCode() != null && !criteria.currencyCode().isBlank()) {
                predicates.add(cb.equal(root.get("currencyCode"), criteria.currencyCode()));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}