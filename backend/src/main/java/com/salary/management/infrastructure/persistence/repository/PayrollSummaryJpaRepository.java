package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
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
}