package com.salary.management.domain.model.values;

import java.util.UUID;

/**
 * Minimal reference to an employee, used in the {@code details} of a {@link ReportWarning}.
 *
 * <p>Holds enough identifying data for a caller to look the employee up without loading the
 * full {@code Employee} aggregate. Not a full employee; not persisted on its own.</p>
 */
public record EmployeeRef(UUID id, String matricule, String name) {
}