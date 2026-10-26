package com.salary.management.application.snapshot;

import com.salary.management.application.dto.EmployeeRefDto;
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
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.mapper.PayrollPeriodPersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.PayrollPeriodJpaRepository;
import lombok.RequiredArgsConstructor;
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
        List<ReportWarning> warnings = new ArrayList<>();
        List<ReportWarning> blockers = new ArrayList<>();

        if (Boolean.TRUE.equals(period.getClosed())) {
            blockers.add(new ReportWarning(
                    ReportWarningCode.PERIOD_CLOSED,
                    ReportWarning.Severity.BLOCKER,
                    "Period is closed; snapshot is only built for open periods",
                    Map.of("periodId", period.getId())));
            return new SnapshotBuildResult(List.of(),new RankingAndPercentileSnapshot(List.of(),List.of()) , warnings, blockers);
        }
        if (!PayrollPeriodFrequency.MONTHLY.equals(period.getFrequency())) {
            blockers.add(new ReportWarning(
                    ReportWarningCode.NOT_MONTHLY_PERIOD,
                    ReportWarning.Severity.BLOCKER,
                    "Period is not a monthly one; snapshot is only built on monthly periods",
                    Map.of("periodId", period.getId())));
            return new SnapshotBuildResult(List.of(),new RankingAndPercentileSnapshot(List.of(),List.of()) , warnings, blockers);
        }
        // We get the inner frequency period
        List<PayrollPeriod> subPeriods = periodJpaRepository.findPeriodsInRangeWithStatusAndFrequency(period.getStartDate(), period.getEndDate(), PayrollPeriodFrequency.BI_WEEKLY)
                                                            .stream().map(periodPersistenceMapper::toDomain).toList();
        // We first collect warnings on the main period
        collectCoverageWarnings(period, warnings, blockers);
        for(PayrollPeriod p:subPeriods){ // We collect warnings on sub periods
            collectCoverageWarnings(p, warnings, blockers);
        }
        // We load conversion data before operations needing it are call
        currencyConverter.initConverter();
        List<PayrollPeriodAggregate> aggregates = buildAggregates(period, blockers);
        RankingAndPercentileSnapshot rankings = buildRankings(period);

        return new SnapshotBuildResult(aggregates, rankings, warnings, blockers);
    }

    // ------------------------------------------------------------------
    // Coverage
    // ------------------------------------------------------------------

    private void collectCoverageWarnings(PayrollPeriod period,
                                         List<ReportWarning> warnings,
                                         List<ReportWarning> blockers) {
        int[] missingCount = new int[1];
        int[] unvalidatedCount = new int[1];

        List<EmployeeRefDto> missing = findMissingEmployees(period, missingCount);
        List<EmployeeRefDto> unvalidated = findUnvalidatedEmployees(period, unvalidatedCount);

        if (missingCount[0] > 0) {
            warnings.add(new ReportWarning(
                    ReportWarningCode.MISSING_PAYROLL_SUMMARY,
                    ReportWarning.Severity.WARNING,
                    "Some employees with an in-effect assignment have no payroll summary",
                    Map.of("count", missingCount[0],
                            "employees", missing)));
        }
        if (unvalidatedCount[0] > 0) {
            blockers.add(new ReportWarning(
                    ReportWarningCode.UNVALIDATED_PAYROLL_SUMMARY,
                    ReportWarning.Severity.BLOCKER,
                    "Some employees have a non-VALIDATED payroll summary",
                    Map.of("count", unvalidatedCount[0],
                            "employees", unvalidated)));
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
        List<GroupedSummary> groups = aggregateRepository.aggregateByGroup(period.getId(), period.getStartDate(), period.getEndDate());
        List<PayrollPeriodAggregate> aggregates = new ArrayList<>();
        boolean stopConvertingAmount = false;

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
            if(!stopConvertingAmount) {
                try {
                    reference = currencyConverter.toReference(original, g.currency());
                } catch (IllegalArgumentException ex) {
                    if (ex.getMessage().equals(CurrencyConverter.RATE_NOT_FOUND)) {
                        blockers.add(new ReportWarning(
                                ReportWarningCode.MISSING_FX_RATE,
                                ReportWarning.Severity.BLOCKER,
                                "No FX rate for currency " + g.currency(),
                                Map.of("currency", g.currency(),
                                        "reason", ex.getMessage())));
                    }else if (ex.getMessage().equals(CurrencyConverter.REFERENCE_NULL)) {
                        stopConvertingAmount =  true;
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
        return aggregates;
    }

    // ------------------------------------------------------------------
    // Rankings
    // ------------------------------------------------------------------

    private RankingAndPercentileSnapshot buildRankings(PayrollPeriod period) {
        List<PayrollPeriodEarnerRanking> rankings = new ArrayList<>();
        List<PayrollPeriodEarnerPercentile> percentiles = new ArrayList<>();
        var result = rankingRepository.rankAndPercentile(period.getId(), currencyConverter.getReferenceCurrency(), null, RANKING_LIMIT);
        if(result != null && result.rankings() != null){
            rankings.addAll(result.rankings());
        }
        if(result != null && result.percentiles() != null){
            percentiles.addAll(result.percentiles());
        }
        result = rankingRepository.rankAndPercentile(period.getId(), currencyConverter.getReferenceCurrency(), GroupKey.COUNTRY, RANKING_LIMIT);
        if(result != null && result.rankings() != null){
            rankings.addAll(result.rankings());
        }
        if(result != null && result.percentiles() != null){
            percentiles.addAll(result.percentiles());
        }
        result = rankingRepository.rankAndPercentile(period.getId(), currencyConverter.getReferenceCurrency(), GroupKey.DEPARTMENT, RANKING_LIMIT);
        if(result != null && result.rankings() != null){
            rankings.addAll(result.rankings());
        }
        if(result != null && result.percentiles() != null){
            percentiles.addAll(result.percentiles());
        }
        result = rankingRepository.rankAndPercentile(period.getId(), currencyConverter.getReferenceCurrency(), GroupKey.ROLE, RANKING_LIMIT);
        if(result != null && result.rankings() != null){
            rankings.addAll(result.rankings());
        }
        if(result != null && result.percentiles() != null){
            percentiles.addAll(result.percentiles());
        }
        result = rankingRepository.rankAndPercentile(period.getId(), currencyConverter.getReferenceCurrency(), GroupKey.EMPLOYMENT_TYPE, RANKING_LIMIT);
        if(result != null && result.rankings() != null){
            rankings.addAll(result.rankings());
        }
        if(result != null && result.percentiles() != null){
            percentiles.addAll(result.percentiles());
        }
        return new RankingAndPercentileSnapshot(rankings, percentiles);
    }


}