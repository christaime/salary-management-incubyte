package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.embeddable.AmountsEmbeddable;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity;
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
     * One row per missing (assignment, period) pair in the range: an in-effect
     * assignment whose cadence matches the period's, covering the period, with no
     * existing payroll summary for (employee, period).
     *
     * <p>Returns detached {@link PayrollSummaryEntity} stubs built via the all-args
     * constructor. {@code id} is {@code null}, {@code amounts} is {@code null}, and
     * {@code status} is {@link PayrollSummaryStatus#DRAFT} — supplied by the caller
     * as the {@code :amounts} and {@code :status} parameters because JPQL cannot
     * inline an embeddable or an enum constant in {@code SELECT NEW}. Not managed;
     * do not persist as-is.</p>
     *
     * <p>Overlap on both sides; no {@code closed} filter (an existing summary
     * suppresses the stub regardless of period state). Optional collection filters
     * must be {@code null} (not empty) when unused.</p>
     */
    @Query("""
        SELECT NEW com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity(
            e,
            p,
            a.id,
            a.countryId,
            a.departmentId,
            a.roleId,
            a.employmentType)
        FROM EmployeePeriodicAssignmentEntity a
        JOIN EmployeeEntity e ON e.id = a.employeeId
        JOIN PayrollPeriodEntity p
          ON p.frequency = a.payrollFrequency
         AND a.startDate <= p.endDate
         AND (a.endDate IS NULL OR a.endDate >= p.startDate)
        WHERE a.inEffect = true
          AND p.startDate <= :periodEnd
          AND p.endDate >= :periodStart
          AND (:frequency IS NULL OR a.payrollFrequency = :frequency)
          AND NOT EXISTS (
              SELECT 1 FROM PayrollSummaryEntity s
              WHERE s.employee.id = a.employeeId
                AND s.payrollPeriod.id = p.id)
          AND (:employeeId IS NULL OR a.employeeId = :employeeId)
          AND (:countryIds IS NULL OR a.countryId IN :countryIds)
          AND (:departmentIds IS NULL OR a.departmentId IN :departmentIds)
          AND (:roleIds IS NULL OR a.roleId IN :roleIds)
          AND (:employmentTypes IS NULL OR a.employmentType IN :employmentTypes)
        """)
    Page<PayrollSummaryEntity> findMissingSummaryPage(
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