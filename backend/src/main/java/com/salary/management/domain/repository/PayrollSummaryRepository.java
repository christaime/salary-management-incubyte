package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link PayrollSummary}.
 *
 * <p>Besides the standard CRUD shape, the port exposes three read paths:</p>
 * <ul>
 *   <li>{@link #list(PayrollSummaryCriteria)} and {@link #page(PayrollSummaryCriteria)} for
 *       persisted summaries filtered by the criteria. When
 *       {@link PayrollSummaryCriteria#nonExistent()} is {@code false} (the default), the
 *       criteria is turned into a JPA specification over {@code payroll_summaries}.</li>
 *   <li>{@link #pageNonExistent(PayrollSummaryCriteria)} for the HR question "which payrolls
 *       did I not fill in for period X?". It queries in-effect assignments matching the
 *       period's frequency and returns, as synthetic summaries with zero amounts and
 *       {@code DRAFT} status, the ones that have no summary for the period. Paginated only:
 *       there is no non-paginated form.</li>
 * </ul>
 */
public interface PayrollSummaryRepository {

    PayrollSummary save(PayrollSummary payrollSummary);

    Optional<PayrollSummary> findById(UUID id);

    Optional<PayrollSummary> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId, UUID payrollPeriodId);

    List<PayrollSummary> list(PayrollSummaryCriteria criteria);

    DataPage<PayrollSummary> page(PayrollSummaryCriteria criteria);

    /**
     * Paginated list of payroll summaries that should exist for the criteria's
     * {@code payrollPeriodId} but do not, computed from the in-effect assignments. Each
     * returned summary has zero amounts, {@code DRAFT} status, and the employee and
     * assignment the missing summary would have been for.
     *
     * <p>The criteria must have {@code nonExistent = true} and a {@code payrollPeriodId};
     * otherwise {@link IllegalArgumentException}.</p>
     */
    DataPage<PayrollSummary> pageNonExistent(PayrollSummaryCriteria criteria);

    void deleteById(UUID id);
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
    LocalDate latestLinkedPeriodStart(UUID assignmentId);

    /**
     * Returns whether at least one payroll summary (any status) is attached
     * to the given assignment.
     *
     * @param assignmentId the employee periodic assignment id
     * @return {@code true} when a summary exists
     */
    boolean hasAnySummaryForAssignment(UUID assignmentId);

    /**
     * Sets the summary's status to {@code newStatus} if its current status is
     * {@code expectedStatus}. Returns the number of rows affected (0 or 1).
     *
     * <p>Only the status column is written; all other columns are left untouched.
     * This is the correct way to transition a summary without re-validating or
     * re-writing its amounts.</p>
     */
    int updateStatus(UUID id, PayrollSummaryStatus expectedStatus, PayrollSummaryStatus newStatus);
}

