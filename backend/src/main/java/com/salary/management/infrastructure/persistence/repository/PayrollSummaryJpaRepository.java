package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity;
import com.salary.management.infrastructure.persistence.projection.SummaryValidationRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Spring Data repository for {@link PayrollSummaryEntity}. */
@Repository
public interface PayrollSummaryJpaRepository
        extends JpaRepository<PayrollSummaryEntity, UUID>,
        JpaSpecificationExecutor<PayrollSummaryEntity> {

    /**
     * Finds a summary by employee and payroll period.
     *
     * @param employeeId      employee identifier
     * @param payrollPeriodId payroll period identifier
     * @return matching summary, or empty
     */
    Optional<PayrollSummaryEntity> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId,
            UUID payrollPeriodId);

    /**
     * Returns the maximum {@code startDate} of the payroll periods whose
     * VALIDATED summaries are attached to the given assignment, or
     * {@code null} if no such summary exists.
     *
     * <p>Used to enforce "the assignment's endDate must not be earlier than
     * the latest linked payroll period's start".</p>
     *
     * @param assignmentId the employee periodic assignment id
     * @return the latest linked payroll period's start date, or {@code null}
     */
    @Query("""
            SELECT MAX(p.startDate)
            FROM PayrollSummaryEntity s
            INNER JOIN PayrollPeriodEntity p ON p.id = s.payrollPeriod.id
            WHERE s.employeePeriodicAssignmentId = :assignmentId
              AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
            """)
    LocalDate latestLinkedPeriodStart(@Param("assignmentId") UUID assignmentId);

    @Query("""
            SELECT COUNT(s) > 0
            FROM PayrollSummaryEntity s
            WHERE s.employeePeriodicAssignmentId = :assignmentId
            """)
    boolean hasAnySummaryForAssignment(@Param("assignmentId") UUID assignmentId);

    @Modifying
    @Query("""
        UPDATE PayrollSummaryEntity s
        SET s.status = :newStatus
        WHERE s.id = :id
          AND s.status = :expectedStatus
        """)
    int updateStatus(
            @Param("id") UUID id,
            @Param("expectedStatus") PayrollSummaryStatus expectedStatus,
            @Param("newStatus") PayrollSummaryStatus newStatus);

    /**
     * Page of DRAFT validation rows whose payroll period is fully contained in
     * {@code [rangeStart, rangeEnd]}, filtered by the optional criteria.
     * Keyset-paginated on {@code s.id}.
     *
     * <p>Status is fixed to DRAFT in the query. The period predicate is
     * containment ({@code p.startDate >= :rangeStart AND p.endDate <= :rangeEnd}),
     * not overlap: a straddling period is excluded. Either bound may be null.
     * {@code LEFT JOIN} on the assignment so orphan summaries surface with null
     * assignment fields instead of being silently dropped. Do not pass a
     * {@code Sort} — the query's {@code ORDER BY s.id} is the keyset order.</p>
     */
    @Query("""
        SELECT NEW com.salary.management.infrastructure.persistence.projection.SummaryValidationRow(
            s.id, s.status,
            p.id, p.closed, p.startDate, p.endDate,
            a.id, a.inEffect, a.startDate, a.endDate)
        FROM PayrollSummaryEntity s
        JOIN s.payrollPeriod p
        LEFT JOIN EmployeePeriodicAssignmentEntity a
          ON a.id = s.employeePeriodicAssignmentId
        WHERE s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.DRAFT
          AND (:rangeStart IS NULL OR p.startDate >= :rangeStart)
          AND (:rangeEnd IS NULL OR p.endDate <= :rangeEnd)
          AND (:employeeId IS NULL OR s.employee.id = :employeeId)
          AND (:countryIds IS NULL OR a.countryId IN :countryIds)
          AND (:departmentIds IS NULL OR a.departmentId IN :departmentIds)
          AND (:roleIds IS NULL OR a.roleId IN :roleIds)
          AND (:employmentTypes IS NULL OR a.employmentType IN :employmentTypes)
          AND (:frequency IS NULL OR a.payrollFrequency = :frequency)
          AND (:afterId IS NULL OR s.id > :afterId)
        ORDER BY s.id
        """)
    Page<SummaryValidationRow> findValidationRows(
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd,
            @Param("employeeId") UUID employeeId,
            @Param("countryIds") Collection<UUID> countryIds,
            @Param("departmentIds") Collection<UUID> departmentIds,
            @Param("roleIds") Collection<UUID> roleIds,
            @Param("employmentTypes") Collection<EmploymentType> employmentTypes,
            @Param("frequency") PayrollPeriodFrequency frequency,
            @Param("afterId") UUID afterId,
            Pageable pageable);

    /**
     * Bulk status transition guarded on the expected current status.
     *
     * @return number of rows actually updated ({@code <= ids.size()}); a smaller
     *         count means some rows were concurrently changed or deleted
     */
    @Modifying
    @Query("""
        UPDATE PayrollSummaryEntity s
           SET s.status = :newStatus
         WHERE s.id IN :ids
           AND s.status = :expectedStatus
        """)
    int bulkUpdateStatus(@Param("ids") Collection<UUID> ids,
                         @Param("expectedStatus") PayrollSummaryStatus expectedStatus,
                         @Param("newStatus") PayrollSummaryStatus newStatus);

    /**
     * Returns the subset of {@code ids} currently in {@code status}. Used only to
     * reconcile a bulk update whose affected-row count is lower than the requested
     * count (concurrent validation / deletion).
     */
    @Query("""
        SELECT s.id FROM PayrollSummaryEntity s
         WHERE s.id IN :ids
           AND s.status = :status
        """)
    Set<UUID> findIdsWithStatus(@Param("ids") Collection<UUID> ids,
                                @Param("status") PayrollSummaryStatus status);
}