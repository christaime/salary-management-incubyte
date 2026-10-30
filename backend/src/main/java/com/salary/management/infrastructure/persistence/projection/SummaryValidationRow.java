package com.salary.management.infrastructure.persistence.projection;

import com.salary.management.domain.model.enums.PayrollSummaryStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Flat projection used by the bulk-validation path.
 *
 * <p>Carries exactly the scalar fields the validation rules need — nothing else.
 * Built via {@code SELECT NEW} from
 * {@link com.salary.management.infrastructure.persistence.repository.PayrollSummaryJpaRepository#findValidationRows}.
 * {@code assignmentId} is {@code null} when the summary points at a missing
 * assignment (the query uses a {@code LEFT JOIN}), in which case
 * {@code assignmentInEffect}, {@code assignmentStart}, and {@code assignmentEnd}
 * are also {@code null}.</p>
 */
public record SummaryValidationRow(
        UUID summaryId,
        PayrollSummaryStatus status,
        UUID periodId,
        Boolean periodClosed,
        LocalDate periodStart,
        LocalDate periodEnd,
        UUID assignmentId,
        Boolean assignmentInEffect,
        LocalDate assignmentStart,
        LocalDate assignmentEnd) {
}