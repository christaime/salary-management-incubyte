package com.salary.management.domain.repository;

import com.salary.management.domain.model.EmployeePeriodicAssignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link EmployeePeriodicAssignment} aggregates.
 */
public interface EmployeePeriodicAssignmentRepository {

    EmployeePeriodicAssignment save(EmployeePeriodicAssignment assignment);

    Optional<EmployeePeriodicAssignment> findById(UUID id);

    List<EmployeePeriodicAssignment> findByEmployeeId(UUID employeeId);

    void deleteById(UUID id);
}