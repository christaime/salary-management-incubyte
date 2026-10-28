package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link EmployeePeriodicAssignment} aggregates. */
public interface EmployeePeriodicAssignmentRepository {

    EmployeePeriodicAssignment save(EmployeePeriodicAssignment assignment);

    Optional<EmployeePeriodicAssignment> findById(UUID id);

    /**
     * Returns the latest in-effect assignment of the given employee whose
     * {@code startDate} falls inside {@code [startDate, endDate]} and whose
     * {@code endDate} is {@code null} or {@code <= endDate}.
     *
     * @param employeeId the employee id
     * @param startDate  inclusive lower bound of the query range
     * @param endDate    inclusive upper bound of the query range
     * @return the latest qualifying assignment, or empty
     */
    Optional<EmployeePeriodicAssignment> findByEmployeeIdAndDateRange(
            UUID employeeId, LocalDate startDate, LocalDate endDate);

    List<EmployeePeriodicAssignment> findByEmployeeId(UUID employeeId);

    List<EmployeePeriodicAssignment> list(EmployeePeriodicAssignmentCriteria criteria);

    DataPage<EmployeePeriodicAssignment> page(EmployeePeriodicAssignmentCriteria criteria);

    void deleteById(UUID id);
}