package com.salary.management.infrastructure.persistence.adapter.utils;

import com.salary.management.common.pagination.Criteria;
import com.salary.management.common.pagination.SortDirection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Translates a domain {@link Criteria} into Spring Data {@link Sort} and {@link Pageable}.
 *
 * <p>Sort field validation lives here: an unknown or missing field silently falls back to the
 * criteria's default field and direction. No exceptions are thrown.</p>
 */
public final class CriteriaUtils {

    private CriteriaUtils() {
    }

    /**
     * Builds a Spring Data {@link Sort} from the criteria, falling back to the default field
     * when the requested field is missing or not in the allow-list.
     *
     * @param criteria source criteria
     * @return Spring Data sort
     */
    public static Sort toSpringSort(Criteria criteria) {
        String field = criteria.sortBy();
        SortDirection direction = criteria.sortDirection();

        if (field == null || !criteria.sortableFields().contains(field)) {
            return Sort.by(
                    toSpringDirection(criteria.defaultSortDirection()),
                    criteria.defaultSortField()
            );
        }
        return Sort.by(
                toSpringDirection(direction != null ? direction : SortDirection.ASC),
                field
        );
    }

    /**
     * Builds a Spring Data {@link Pageable} from the criteria's page, size, and sort.
     *
     * @param criteria source criteria
     * @return Spring Data pageable
     */
    public static Pageable toPageable(Criteria criteria) {
        return PageRequest.of(criteria.page(), criteria.size(), toSpringSort(criteria));
    }

    private static Sort.Direction toSpringDirection(SortDirection direction) {
        return direction == SortDirection.DESC
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
    }
}