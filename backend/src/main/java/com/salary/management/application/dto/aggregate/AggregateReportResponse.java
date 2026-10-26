package com.salary.management.application.dto.aggregate;

import com.salary.management.application.dto.DateRange;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.values.ReportWarning;

import java.util.List;

/**
 * Response body for {@code POST /reports/aggregate}.
 *
 * <p>{@code kind} is always {@code "AGGREGATE"} for this endpoint. {@code groupKeys} echoes
 * the requested dimensions in the order supplied by the client. {@code referenceCurrency}
 * is the currency the amounts are expressed in (USD).</p>
 *
 * @param kind              report kind discriminator
 * @param dateRange         inclusive date range the report covers
 * @param groupKeys         grouping dimensions that were used
 * @param referenceCurrency currency the amounts are expressed in
 * @param lines             aggregated lines, one per distinct key combination
 * @param warnings          non-blocking warnings raised while building the report
 */
public record AggregateReportResponse(
        ReportKind kind,
        DateRange dateRange,
        List<GroupKey> groupKeys,
        String referenceCurrency,
        List<AggregateLineDto> lines,
        List<ReportWarning> warnings) {
}