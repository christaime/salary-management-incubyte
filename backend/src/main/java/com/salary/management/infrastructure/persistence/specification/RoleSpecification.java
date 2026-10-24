package com.salary.management.infrastructure.persistence.specification;

import com.salary.management.domain.repository.criteria.RoleCriteria;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/** Builds JPA specifications for {@link RoleEntity} from {@link RoleCriteria}. */
public final class RoleSpecification {

    /**
     * Builds a specification from the criteria's filters. Returns a conjunction when no
     * filter is set.
     *
     * @param criteria source criteria
     * @return specification
     */
    public static Specification<RoleEntity> build(RoleCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
                String pattern = "%" + criteria.keyword().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("code")), pattern)
                ));
            }

            if (criteria.code() != null && !criteria.code().isBlank()) {
                predicates.add(cb.equal(
                        cb.upper(root.get("code")),
                        criteria.code().toUpperCase()
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}