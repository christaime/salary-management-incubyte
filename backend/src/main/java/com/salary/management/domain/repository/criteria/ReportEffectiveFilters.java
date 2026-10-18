package com.salary.management.domain.repository.criteria;

import com.salary.management.domain.model.enums.EmploymentType;

import java.util.Set;
import java.util.UUID;

/**
 * Selection filters applied to report queries, after group-key reconciliation.
 *
 * <p>Built by the report service: any filter whose dimension also appears in the requested
 * group keys is dropped, so a dimension is never both grouped and filtered. A {@code null}
 * or empty set means no restriction on that dimension.</p>
 *
 * @param countryIds      country filter, or {@code null}
 * @param departmentIds   department filter, or {@code null}
 * @param roleIds         role filter, or {@code null}
 * @param employmentTypes employment type filter, or {@code null}
 */
public record ReportEffectiveFilters(
        Set<UUID> countryIds,
        Set<UUID> departmentIds,
        Set<UUID> roleIds,
        Set<EmploymentType> employmentTypes) {
}