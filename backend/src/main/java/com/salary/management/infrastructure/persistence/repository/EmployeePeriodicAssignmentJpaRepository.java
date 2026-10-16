package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link EmployeePeriodicAssignmentEntity}. */
@Repository
public interface EmployeePeriodicAssignmentJpaRepository
        extends JpaRepository<EmployeePeriodicAssignmentEntity, UUID>,
        JpaSpecificationExecutor<EmployeePeriodicAssignmentEntity> {

    /**
     * Finds all assignments for an employee.
     *
     * @param employeeId employee identifier
     * @return matching assignments
     */
    List<EmployeePeriodicAssignmentEntity> findByEmployeeId(UUID employeeId);

    /**
     * Returns whether any payroll summary points at this assignment.
     *
     * @param assignmentId assignment identifier
     * @return {@code true} when at least one summary references it
     */
    @Query("""
            SELECT COUNT(s) > 0
            FROM PayrollSummaryEntity s
            WHERE s.employeePeriodicAssignmentId = :assignmentId
            """)
    boolean isReferencedByPayrollSummary(@Param("assignmentId") UUID assignmentId);

    /**
     * Returns whether the assignment is referenced anywhere in the schema.
     *
     * @param assignmentId assignment identifier
     * @return {@code true} when at least one row references it
     */
    default boolean isReferenced(UUID assignmentId) {
        return isReferencedByPayrollSummary(assignmentId);
    }
}