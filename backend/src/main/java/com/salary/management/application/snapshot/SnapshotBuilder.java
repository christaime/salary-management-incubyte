package com.salary.management.application.snapshot;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.enums.ReportWarningCode;
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
import com.salary.management.infrastructure.persistence.repository.DynamicRankingBuilder;
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
            return new SnapshotBuildResult(List.of(), List.of(), warnings, blockers);
        }

        collectCoverageWarnings(period, warnings, blockers);
        // We load conversion data before operations needing it are call
        currencyConverter.initConverter();
        List<PayrollPeriodAggregate> aggregates = buildAggregates(period, blockers);
        List<PayrollPeriodEarnerRanking> rankings = buildRankings(period);

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

        List<EmployeeRef> missing = findMissingEmployees(period, missingCount);
        List<EmployeeRef> unvalidated = findUnvalidatedEmployees(period, unvalidatedCount);

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

    private List<EmployeeRef> findMissingEmployees(PayrollPeriod period, int[] countOut) {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(period.getId())
                .nonExistent(true)
                .page(0)
                .size(MAX_EMPLOYEES_IN_DETAILS)
                .build();
        DataPage<PayrollSummary> page = payrollSummaryRepository.pageNonExistent(criteria);
        countOut[0] = (int) page.totalElements();
        List<EmployeeRef> details = new ArrayList<>();
        for (PayrollSummary summary : page.content()) {
            employeeRepository.findById(summary.getEmployeeId())
                    .ifPresent(e -> details.add(
                            new EmployeeRef(e.getId(), e.getMatricule(), e.getName())));
        }
        return details;
    }

    private List<EmployeeRef> findUnvalidatedEmployees(PayrollPeriod period, int[] countOut) {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(period.getId())
                .status(PayrollSummaryStatus.DRAFT)
                .page(0)
                .size(MAX_EMPLOYEES_IN_DETAILS)
                .build();
        DataPage<PayrollSummary> page = payrollSummaryRepository.page(criteria);
        countOut[0] = (int) page.totalElements();
        List<EmployeeRef> details = new ArrayList<>();
        for (PayrollSummary summary : page.content()) {
            employeeRepository.findById(summary.getEmployeeId())
                    .ifPresent(e -> details.add(
                            new EmployeeRef(e.getId(), e.getMatricule(), e.getName())));
        }
        return details;
    }

    // ------------------------------------------------------------------
    // Aggregates
    // ------------------------------------------------------------------

    private List<PayrollPeriodAggregate> buildAggregates(
            PayrollPeriod period, List<ReportWarning> blockers) {
        List<GroupedSummary> groups = aggregateRepository.aggregateByGroup(period.getId());
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

    private List<PayrollPeriodEarnerRanking> buildRankings(PayrollPeriod period) {
        List<PayrollPeriodEarnerRanking> rankings = new ArrayList<>();
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.TOP, null, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.TOP, GroupKey.COUNTRY, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.TOP, GroupKey.DEPARTMENT, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.TOP, GroupKey.ROLE, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.TOP, GroupKey.EMPLOYMENT_TYPE, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.BOTTOM, null, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.BOTTOM, GroupKey.COUNTRY, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.BOTTOM, GroupKey.DEPARTMENT, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.BOTTOM, GroupKey.ROLE, RANKING_LIMIT)));
        rankings.addAll(toRankings(period,rankingRepository.rank(period.getId(), currencyConverter.getReferenceCurrency(), EarnerRankingDirection.BOTTOM, GroupKey.EMPLOYMENT_TYPE, RANKING_LIMIT)));
        return rankings;
    }

    private List<PayrollPeriodEarnerRanking> toRankings(PayrollPeriod period,List<PayrollPeriodEarnerRankingEntity> rankingEntities) {
        List<PayrollPeriodEarnerRanking> out = new ArrayList<>();
        for (PayrollPeriodEarnerRankingEntity row : rankingEntities) {
            Country country = row.getCountryId() != null ? countryRepository.findById(row.getCountryId()).orElseThrow() : null;
            Department department = row.getDepartmentId() != null ? departmentRepository.findById(row.getDepartmentId()).orElseThrow() : null;
            Role role = row.getRoleId() != null ? roleRepository.findById(row.getRoleId()).orElseThrow() : null;
            Employee employee = employeeRepository.findById(row.getEmployeeId()).orElseThrow();
            out.add(PayrollPeriodEarnerRanking.builder()
                    .payrollPeriod(period)
                    .status(PayrollSummaryStatus.DRAFT)
                    .country(country)
                    .department(department)
                    .role(role)
                    .employmentType(row.getEmploymentType())
                    .direction(row.getDirection())
                    .rank(row.getRank())
                    .employee(employee)
                    .originalAmount(Amount.builder().amount(row.getOriginalAmount().getAmount()).currency(row.getOriginalAmount().getCurrency()).build())
                    .referenceAmount(Amount.builder().amount(row.getReferenceAmount().getAmount()).currency(row.getReferenceAmount().getCurrency()).build())
                    .build());
        }
        return out;
    }

}