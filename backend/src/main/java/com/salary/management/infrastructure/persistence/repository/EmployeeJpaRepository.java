package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link EmployeeEntity}. */
@Repository
public interface EmployeeJpaRepository
        extends JpaRepository<EmployeeEntity, UUID>, JpaSpecificationExecutor<EmployeeEntity> {

    /**
     * Finds an employee by matricule.
     *
     * @param matricule business key
     * @return matching employee, or empty
     */
    Optional<EmployeeEntity> findByMatricule(String matricule);

    /**
     * Returns whether an employee with the given matricule exists.
     *
     * @param matricule business key
     * @return {@code true} when present
     */
    boolean existsByMatricule(String matricule);

    /**
     * Returns whether any periodic assignment belongs to this employee.
     *
     * @param employeeId employee identifier
     * @return {@code true} when at least one assignment references it
     */
    @Query("""
            SELECT COUNT(a) > 0
            FROM EmployeePeriodicAssignmentEntity a
            WHERE a.employeeId = :employeeId
            """)
    boolean isReferencedByAssignment(@Param("employeeId") UUID employeeId);

    /**
     * Returns whether any payroll summary belongs to this employee.
     *
     * <p>Navigates the {@code @ManyToOne} to reach the employee's id.</p>
     *
     * @param employeeId employee identifier
     * @return {@code true} when at least one summary references it
     */
    @Query("""
            SELECT COUNT(s) > 0
            FROM PayrollSummaryEntity s
            WHERE s.employee.id = :employeeId
            """)
    boolean isReferencedByPayrollSummary(@Param("employeeId") UUID employeeId);

    /**
     * Returns whether any earner ranking entry belongs to this employee.
     *
     * @param employeeId employee identifier
     * @return {@code true} when at least one ranking references it
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM PayrollPeriodEarnerRankingEntity r
            WHERE r.employeeId = :employeeId
            """)
    boolean isReferencedByRanking(@Param("employeeId") UUID employeeId);

    /**
     * Returns whether the employee is referenced anywhere in the schema.
     *
     * @param employeeId employee identifier
     * @return {@code true} when at least one row references it
     */
    default boolean isReferenced(UUID employeeId) {
        return isReferencedByAssignment(employeeId)
                || isReferencedByPayrollSummary(employeeId)
                || isReferencedByRanking(employeeId);
    }
}