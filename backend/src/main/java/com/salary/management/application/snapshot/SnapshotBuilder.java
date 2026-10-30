package com.salary.management.application.snapshot;

import com.salary.management.application.dto.employee.EmployeeRefDto;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.*;
import com.salary.management.domain.model.enums.*;
import com.salary.management.domain.model.values.*;
import com.salary.management.domain.repository.CountryRepository;
import com.salary.management.domain.repository.DepartmentRepository;
import com.salary.management.domain.repository.EmployeeRepository;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.PayrollSummaryRepository;
import com.salary.management.domain.repository.RoleRepository;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.mapper.PayrollPeriodPersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.PayrollPeriodJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the aggregates and earner rankings of an open payroll period from its VALIDATED
 * payroll summaries.
 *
 * <p>Flow:</p>
 * <ol>
 *   <li>If the period is closed, return a single {@code PERIOD_CLOSED} blocker. Stop.</li>
 *   <li>Coverage check:
 *       <ul>
 *         <li>Employees with an in-effect assignment matching the period's frequency that
 *             have no summary → {@code MISSING_PAYROLL_SUMMARY} (WARNING).</li>
 *         <li>Employees with a non-VALIDATED summary for the period →
 *             {@code UNVALIDATED_PAYROLL_SUMMARY} (BLOCKER).</li>
 *       </ul>
 *       The build continues in both cases.</li>
 *   <li>Aggregate VALIDATED summaries by the five assignment attributes (one DB query).</li>
 *   <li>For each group, convert the original {@code Amounts} to the reference currency. If
 *       the currency has no rate, add a {@code MISSING_FX_RATE} blocker, leave
 *       {@code referenceAmounts} null for that group, and continue with the next group.</li>
 *   <li>Rank VALIDATED summaries per group (top 5, bottom 5) with two DB queries.</li>
 * </ol>
 *
 * <p>Never writes. Never throws on missing data.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SnapshotBuilder {

    private static final int RANKING_LIMIT = 5;
    private static final int MAX_EMPLOYEES_IN_DETAILS = 10;

    private final PayrollSummaryRepository payrollSummaryRepository;
    private final EmployeeRepository employeeRepository;
    private final CountryRepository countryRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PayrollPeriodJpaRepository periodJpaRepository;
    private final PayrollPeriodPersistenceMapper periodPersistenceMapper;
    private final PayrollPeriodAggregateRepository aggregateRepository;
    private final PayrollPeriodEarnerRankingRepository rankingRepository;
    private final CurrencyConverter currencyConverter;

    public SnapshotBuildResult build(PayrollPeriod period) {
        log.info("Building snapshot for period {} ({} .. {}, frequency={}, closed={})",
                period.getId(), period.getStartDate(), period.getEndDate(),
                period.getFrequency(), period.getClosed());

        List<ReportWarning> warnings = new ArrayList<>();
        List<ReportWarning> blockers = new ArrayList<>();

        if (Boolean.TRUE.equals(period.getClosed())) {
            log.warn("Snapshot aborted for period {}: period is closed", period.getId());
            blockers.add(new ReportWarning(
                    ReportWarningCode.PERIOD_CLOSED,
                    ReportWarning.Severity.BLOCKER,
                    "Period is closed; snapshot is only built for open periods",
                    Map.of("periodId", period.getId())));
            return new SnapshotBuildResult(
                    List.of(),
                    new RankingAndPercentileSnapshot(List.of(), List.of()),
                    warnings, blockers);
        }

        if (!PayrollPeriodFrequency.MONTHLY.equals(period.getFrequency())) {
            log.warn("Snapshot aborted for period {}: frequency {} is not MONTHLY",
                    period.getId(), period.getFrequency());
            blockers.add(new ReportWarning(
                    ReportWarningCode.NOT_MONTHLY_PERIOD,
                    ReportWarning.Severity.BLOCKER,
                    "Period is not a monthly one; snapshot is only built on monthly periods",
                    Map.of("periodId", period.getId())));
            return new SnapshotBuildResult(
                    List.of(),
                    new RankingAndPercentileSnapshot(List.of(), List.of()),
                    warnings, blockers);
        }

        // We get the inner frequency period
        List<PayrollPeriod> subPeriods = periodJpaRepository
                .findPeriodsInRangeWithStatusAndFrequency(
                        period.getStartDate(), period.getEndDate(),
                        PayrollPeriodFrequency.BI_WEEKLY)
                .stream().map(periodPersistenceMapper::toDomain).toList();
        log.debug("Found {} bi-weekly sub-period(s) inside period {}",
                subPeriods.size(), period.getId());

        // We first collect warnings on the main period
        collectCoverageWarnings(period, warnings, blockers);
        for (PayrollPeriod p : subPeriods) { // We collect warnings on sub periods
            collectCoverageWarnings(p, warnings, blockers);
        }

        // We load conversion data before operations needing it are called
        try {
            currencyConverter.initConverter();
            log.debug("Currency converter initialized (reference={})",
                    currencyConverter.getReferenceCurrency());
        } catch (RuntimeException ex) {
            // initConverter is not documented to throw, but if it does we want the
            // period id in the log before the exception propagates.
            log.error("Failed to initialize currency converter for period {}",
                    period.getId(), ex);
            throw ex;
        }

        List<PayrollPeriodAggregate> aggregates = buildAggregates(period, blockers);
        RankingAndPercentileSnapshot rankings = buildRankings(period);

        log.info("Snapshot built for period {}: {} aggregate(s), {} ranking(s), "
                        + "{} percentile(s), {} warning(s), {} blocker(s)",
                period.getId(), aggregates.size(),
                rankings.rankings().size(), rankings.percentiles().size(),
                warnings.size(), blockers.size());

        if (!blockers.isEmpty()) {
            log.warn("Snapshot for period {} has {} blocker(s); first: {}",
                    period.getId(), blockers.size(), blockers.get(0).code());
        }

        return new SnapshotBuildResult(aggregates, rankings, warnings, blockers);
    }

    // ------------------------------------------------------------------
    // Coverage
    // ------------------------------------------------------------------

    private void collectCoverageWarnings(PayrollPeriod period,
                                         List<ReportWarning> warnings,
                                         List<ReportWarning> blockers) {
        log.debug("Checking coverage for period {}", period.getId());

        int[] missingCount = new int[1];
        int[] unvalidatedCount = new int[1];

        List<EmployeeRefDto> missing = findMissingEmployees(period, missingCount);
        List<EmployeeRefDto> unvalidated = findUnvalidatedEmployees(period, unvalidatedCount);

        if (missingCount[0] > 0) {
            log.warn("Period {}: {} employee(s) with an in-effect assignment have no "
                            + "payroll summary (showing up to {})",
                    period.getId(), missingCount[0], MAX_EMPLOYEES_IN_DETAILS);
            warnings.add(new ReportWarning(
                    ReportWarningCode.MISSING_PAYROLL_SUMMARY,
                    ReportWarning.Severity.WARNING,
                    "Some employees with an in-effect assignment have no payroll summary",
                    Map.of("count", missingCount[0],
                            "employees", missing)));
        } else {
            log.debug("Period {}: no missing payroll summaries", period.getId());
        }

        if (unvalidatedCount[0] > 0) {
            log.warn("Period {}: {} employee(s) have a non-VALIDATED payroll summary "
                            + "(showing up to {})",
                    period.getId(), unvalidatedCount[0], MAX_EMPLOYEES_IN_DETAILS);
            blockers.add(new ReportWarning(
                    ReportWarningCode.UNVALIDATED_PAYROLL_SUMMARY,
                    ReportWarning.Severity.BLOCKER,
                    "Some employees have a non-VALIDATED payroll summary",
                    Map.of("count", unvalidatedCount[0],
                            "employees", unvalidated)));
        } else {
            log.debug("Period {}: no unvalidated payroll summaries", period.getId());
        }
    }

    private List<EmployeeRefDto> findMissingEmployees(PayrollPeriod period, int[] countOut) {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(period.getId())
                .nonExistent(true)
                .page(0)
                .size(MAX_EMPLOYEES_IN_DETAILS)
                .build();
        DataPage<PayrollSummary> page = payrollSummaryRepository.pageNonExistent(criteria);
        countOut[0] = (int) page.totalElements();
        List<EmployeeRefDto> details = new ArrayList<>();
        for (PayrollSummary summary : page.content()) {
            employeeRepository.findById(summary.getEmployeeId())
                    .ifPresent(e -> details.add(
                            new EmployeeRefDto(e.getId(), e.getMatricule(), e.getName())));
        }
        return details;
    }

    private List<EmployeeRefDto> findUnvalidatedEmployees(PayrollPeriod period, int[] countOut) {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(period.getId())
                .status(PayrollSummaryStatus.DRAFT)
                .page(0)
                .size(MAX_EMPLOYEES_IN_DETAILS)
                .build();
        DataPage<PayrollSummary> page = payrollSummaryRepository.page(criteria);
        countOut[0] = (int) page.totalElements();
        List<EmployeeRefDto> details = new ArrayList<>();
        for (PayrollSummary summary : page.content()) {
            employeeRepository.findById(summary.getEmployeeId())
                    .ifPresent(e -> details.add(
                            new EmployeeRefDto(e.getId(), e.getMatricule(), e.getName())));
        }
        return details;
    }

    // ------------------------------------------------------------------
    // Aggregates
    // ------------------------------------------------------------------

    private List<PayrollPeriodAggregate> buildAggregates(
            PayrollPeriod period, List<ReportWarning> blockers) {
        log.debug("Aggregating VALIDATED summaries for period {}", period.getId());

        List<GroupedSummary> groups = aggregateRepository.aggregateByGroup(
                period.getId(), period.getStartDate(), period.getEndDate());
        log.debug("Period {}: {} aggregate group(s) returned by repository",
                period.getId(), groups.size());

        List<PayrollPeriodAggregate> aggregates = new ArrayList<>();
        boolean stopConvertingAmount = false;
        int missingFxCount = 0;

        for (GroupedSummary g : groups) {
            Country country = countryRepository.findById(g.countryId()).orElseThrow();
            Department department = departmentRepository.findById(g.departmentId()).orElseThrow();
            Role role = roleRepository.findById(g.roleId()).orElseThrow();

            Amounts original = Amounts.builder()
                    .base(g.base())
                    .variable(g.variable())
                    .allowances(g.allowances())
                    .employerTaxes(g.employerTaxes())
                    .currency(g.currency())
                    .build();

            Amounts reference = null;
            if (!stopConvertingAmount) {
                try {
                    reference = currencyConverter.toReference(original, g.currency());
                } catch (IllegalArgumentException ex) {
                    if (ex.getMessage().equals(CurrencyConverter.RATE_NOT_FOUND)) {
                        missingFxCount++;
                        log.warn("Period {}: no FX rate for currency {}; "
                                        + "reference amounts will be null for this group",
                                period.getId(), g.currency());
                        blockers.add(new ReportWarning(
                                ReportWarningCode.MISSING_FX_RATE,
                                ReportWarning.Severity.BLOCKER,
                                "No FX rate for currency " + g.currency(),
                                Map.of("currency", g.currency(),
                                        "reason", ex.getMessage())));
                    } else if (ex.getMessage().equals(CurrencyConverter.REFERENCE_NULL)) {
                        stopConvertingAmount = true;
                        log.error("Period {}: no reference currency configured; "
                                        + "skipping FX conversion for all remaining groups",
                                period.getId());
                        blockers.add(new ReportWarning(
                                ReportWarningCode.MISSING_REFERENCE_CURRENCY,
                                ReportWarning.Severity.BLOCKER,
                                "No reference defined ",
                                Map.of("reason", ex.getMessage())));
                    }
                }
            }

            aggregates.add(PayrollPeriodAggregate.builder()
                    .payrollPeriod(period)
                    .status(PayrollSummaryStatus.DRAFT)
                    .country(country)
                    .department(department)
                    .role(role)
                    .employmentType(g.employmentType())
                    .originalAmounts(original)
                    .referenceAmounts(reference)
                    .employeeCount(g.employeeCount())
                    .build());
        }

        log.debug("Period {}: built {} aggregate(s){}",
                period.getId(), aggregates.size(),
                missingFxCount > 0
                        ? " (" + missingFxCount + " group(s) without FX rate)"
                        : "");

        return aggregates;
    }

    // ------------------------------------------------------------------
    // Rankings
    // ------------------------------------------------------------------

    private RankingAndPercentileSnapshot buildRankings(PayrollPeriod period) {
        String referenceCurrency = currencyConverter.getReferenceCurrency();
        log.debug("Ranking VALIDATED summaries for period {} (reference currency={}, "
                        + "top/bottom={})",
                period.getId(), referenceCurrency, RANKING_LIMIT);

        List<PayrollPeriodEarnerRanking> rankings = new ArrayList<>();
        List<PayrollPeriodEarnerPercentile> percentiles = new ArrayList<>();

        // Global (no group key).
        appendRankings(period, "GLOBAL", null, rankings, percentiles);
        appendRankings(period, "COUNTRY", GroupKey.COUNTRY, rankings, percentiles);
        appendRankings(period, "DEPARTMENT", GroupKey.DEPARTMENT, rankings, percentiles);
        appendRankings(period, "ROLE", GroupKey.ROLE, rankings, percentiles);
        appendRankings(period, "EMPLOYMENT_TYPE", GroupKey.EMPLOYMENT_TYPE, rankings, percentiles);

        log.debug("Period {}: {} ranking(s), {} percentile(s) collected",
                period.getId(), rankings.size(), percentiles.size());

        return new RankingAndPercentileSnapshot(rankings, percentiles);
    }

    /**
     * Runs {@code rankAndPercentile} for one group key and appends its results.
     * Extracted to remove the five-times duplication of the null-check dance.
     */
    private void appendRankings(PayrollPeriod period,
                                String groupLabel,
                                GroupKey groupKey,
                                List<PayrollPeriodEarnerRanking> rankings,
                                List<PayrollPeriodEarnerPercentile> percentiles) {
        var result = rankingRepository.rankAndPercentile(
                period.getId(),
                currencyConverter.getReferenceCurrency(),
                groupKey,
                RANKING_LIMIT);

        if (result == null) {
            log.warn("Period {}: ranking repository returned null for group {}",
                    period.getId(), groupLabel);
            return;
        }

        int r = result.rankings() == null ? 0 : result.rankings().size();
        int p = result.percentiles() == null ? 0 : result.percentiles().size();
        log.trace("Period {}: group {} produced {} ranking(s) and {} percentile(s)",
                period.getId(), groupLabel, r, p);

        if (result.rankings() != null) {
            rankings.addAll(result.rankings());
        }
        if (result.percentiles() != null) {
            percentiles.addAll(result.percentiles());
        }
    }
}