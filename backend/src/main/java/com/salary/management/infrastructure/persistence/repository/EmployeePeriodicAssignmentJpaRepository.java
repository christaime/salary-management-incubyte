package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * Page of in-effect assignments that should have a payroll summary for the given period
     * but do not.
     *
     * <p>Filters:</p>
     * <ul>
     *   <li>{@code in_effect = true}</li>
     *   <li>{@code payroll_frequency = :frequency} (the period's cadence)</li>
     *   <li>assignment covers the period: {@code start_date <= :periodEnd} and
     *       ({@code end_date IS NULL} or {@code end_date >= :periodStart})</li>
     *   <li>no {@code payroll_summaries} row exists for the same employee and period</li>
     *   <li>optional filters: {@code employeeId}, {@code employeeMatricule}, and any of
     *       country / department / role / employmentType</li>
     * </ul>
     *
     * <p>Sorting is fixed by the calling adapter (assignment {@code startDate ASC}).
     * The caller supplies a {@link Pageable} with the page index and size.</p>
     */
    @Query("""
            SELECT a FROM EmployeePeriodicAssignmentEntity a
            WHERE a.inEffect = true
              AND a.payrollFrequency = :frequency
              AND a.startDate <= :periodEnd
              AND (a.endDate IS NULL OR a.endDate >= :periodStart)
              AND NOT EXISTS (
                  SELECT 1 FROM PayrollSummaryEntity s
                  WHERE s.employee.id = a.employeeId
                    AND s.payrollPeriod.id = :payrollPeriodId
              )
              AND (:employeeId IS NULL OR a.employeeId = :employeeId)
              AND (:countryIds IS NULL OR a.countryId IN :countryIds)
              AND (:departmentIds IS NULL OR a.departmentId IN :departmentIds)
              AND (:roleIds IS NULL OR a.roleId IN :roleIds)
              AND (:employmentTypes IS NULL OR a.employmentType IN :employmentTypes)
            """)
    Page<EmployeePeriodicAssignmentEntity> findMissingSummaryPage(
            @Param("payrollPeriodId") UUID payrollPeriodId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("frequency") PayrollPeriodFrequency frequency,
            @Param("employeeId") UUID employeeId,
            @Param("countryIds") Collection<UUID> countryIds,
            @Param("departmentIds") Collection<UUID> departmentIds,
            @Param("roleIds") Collection<UUID> roleIds,
            @Param("employmentTypes") Collection<EmploymentType> employmentTypes,
            Pageable pageable);

    /**
     * Returns the assignments of the given employee whose
     * {@code start_date} falls inside {@code [startDate, endDate]} and whose
     * {@code end_date} is either {@code null} or {@code <= endDate}, ordered
     * by {@code start_date DESC}.
     *
     * <p>Returning a list (rather than {@code Optional}) avoids a
     * {@code NonUniqueResultException} when more than one assignment
     * satisfies the predicate; the adapter picks the latest one.</p>
     *
     * @param employeeId the employee id
     * @param startDate  inclusive lower bound of the query range
     * @param endDate    inclusive upper bound of the query range
     * @return qualifying assignments, latest {@code start_date} first
     */
    @Query("""
            SELECT a FROM EmployeePeriodicAssignmentEntity a
            WHERE a.employeeId = :employeeId
              AND a.startDate <= :startDate
              AND (a.endDate IS NULL OR a.endDate >= :endDate)
            ORDER BY a.startDate DESC
            """)
    List<EmployeePeriodicAssignmentEntity> findOverlappingAssignments(
            @Param("employeeId") UUID employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}