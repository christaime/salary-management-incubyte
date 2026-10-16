package com.salary.management.common.pagination;

import java.util.Set;

/**
 * Contract for a paged, sorted query passed from the domain to a repository port.
 *
 * <p>Implementations are entity-specific and carry optional filters alongside sort and pagination
 * parameters. The persistence layer validates {@link #sortBy()} against {@link #sortableFields()}
 * and falls back to {@link #defaultSortField()} when the requested field is missing or unknown.</p>
 */
public interface Criteria {

    /**
     * Requested sort field, or {@code null} when unspecified.
     *
     * @return requested sort field
     */
    String sortBy();

    /**
     * Requested sort direction, or {@code null} when unspecified.
     *
     * @return requested sort direction
     */
    SortDirection sortDirection();

    /**
     * Allow-list of fields that may be used as a sort key.
     *
     * @return allowed sort fields
     */
    Set<String> sortableFields();

    /**
     * Sort field used when {@link #sortBy()} is missing or not in {@link #sortableFields()}.
     *
     * @return default sort field
     */
    String defaultSortField();

    /**
     * Sort direction applied to {@link #defaultSortField()}.
     *
     * @return default sort direction
     */
    SortDirection defaultSortDirection();

    /**
     * Zero-based page index.
     *
     * @return page index
     */
    int page();

    /**
     * Requested page size.
     *
     * @return page size
     */
    int size();
}