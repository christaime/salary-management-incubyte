package com.salary.management.common.pagination;

/**
 * Sort direction requested by a {@link Criteria}.
 *
 * <p>Consumed by the persistence layer to translate into Spring Data's sort direction. A
 * {@code null} direction at the call site is treated as {@link #ASC}.</p>
 */
public enum SortDirection {

    /** Ascending order. */
    ASC,

    /** Descending order. */
    DESC
}