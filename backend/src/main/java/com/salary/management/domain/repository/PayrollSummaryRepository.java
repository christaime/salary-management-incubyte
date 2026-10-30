package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.projection.SummaryValidationRow;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence port for {@link PayrollSummary}.
 *
 * <p>Besides the standard CRUD shape, the port exposes these read paths:</p>
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
 *   <li>{@link #findValidationRows} for the bulk-validation use case. It returns a flat
 *       projection (summary id, status, period dates, assignment dates/state) so the
 *       service can check every rule without loading entities. Keyset-paginated on
 *       summary id.</li>
 * </ul>
 */
public interface PayrollSummaryRepository {

    // ------------------------------------------------------------------
    // Standard CRUD
    // ------------------------------------------------------------------

    PayrollSummary save(PayrollSummary payrollSummary);

    Optional<PayrollSummary> findById(UUID id);

    Optional<PayrollSummary> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId, UUID payrollPeriodId);

    void deleteById(UUID id);

    // ------------------------------------------------------------------
    // Criteria-based reads
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Assignment-linked reads
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Bulk validation read
    // ------------------------------------------------------------------

    /**
     * Page of DRAFT validation rows whose payroll period is fully contained in
     * {@code [rangeStart, rangeEnd]}, filtered by the optional criteria.
     * Keyset-paginated on {@code summaryId}.
     *
     * <p>Only DRAFT summaries are ever returned. Periods must be contained in the
     * range, not merely overlapping. Orphan summaries (missing assignment) are
     * returned with null assignment fields so the caller can report them. Pass
     * {@code afterId = null} for the first page, then the last {@code summaryId}
     * of the previous page; empty filter collections must be normalized to null.</p>
     */
    DataPage<SummaryValidationRow> findValidationRows(
            LocalDate rangeStart,
            LocalDate rangeEnd,
            UUID employeeId,
            Set<UUID> anyCountryIds,
            Set<UUID> anyDepartmentIds,
            Set<UUID> anyRoleIds,
            Set<EmploymentType> anyEmploymentTypes,
            PayrollPeriodFrequency payrollFrequency,
            UUID afterId,
            int page,
            int size);

    // ------------------------------------------------------------------
    // Status transitions
    // ------------------------------------------------------------------

    /**
     * Sets the summary's status to {@code newStatus} if its current status is
     * {@code expectedStatus}. Returns the number of rows affected (0 or 1).
     *
     * <p>Only the status column is written; all other columns are left untouched.
     * This is the correct way to transition a summary without re-validating or
     * re-writing its amounts.</p>
     */
    int updateStatus(UUID id, PayrollSummaryStatus expectedStatus, PayrollSummaryStatus newStatus);

    /**
     * Bulk status transition guarded on the expected current status.
     *
     * <p>Applied as a single {@code UPDATE ... WHERE id IN (...) AND status = :expected}.
     * The returned count may be lower than {@code ids.size()} when some rows
     * were concurrently changed or deleted; callers must reconcile using
     * {@link #findIdsWithStatus} to report accurate per-row outcomes.</p>
     *
     * @param ids            summary identifiers
     * @param expectedStatus status each row must currently have
     * @param newStatus      target status
     * @return the number of rows actually updated
     */
    int bulkUpdateStatus(Collection<UUID> ids,
                         PayrollSummaryStatus expectedStatus,
                         PayrollSummaryStatus newStatus);

    /**
     * Returns the subset of {@code ids} that currently have {@code status}.
     *
     * <p>Intended only for reconciling a bulk update whose affected-row count
     * is lower than the requested count. Do not use as a primary read.</p>
     *
     * @param ids    summary identifiers
     * @param status status to match
     * @return the matching ids (possibly empty)
     */
    Set<UUID> findIdsWithStatus(Collection<UUID> ids, PayrollSummaryStatus status);
}