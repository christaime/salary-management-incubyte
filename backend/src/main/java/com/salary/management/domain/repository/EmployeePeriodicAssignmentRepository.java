package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link EmployeePeriodicAssignment} aggregates. */
public interface EmployeePeriodicAssignmentRepository {

    EmployeePeriodicAssignment save(EmployeePeriodicAssignment assignment);

    Optional<EmployeePeriodicAssignment> findById(UUID id);

    List<EmployeePeriodicAssignment> findByEmployeeId(UUID employeeId);

    List<EmployeePeriodicAssignment> list(EmployeePeriodicAssignmentCriteria criteria);

    DataPage<EmployeePeriodicAssignment> page(EmployeePeriodicAssignmentCriteria criteria);

    void deleteById(UUID id);
}