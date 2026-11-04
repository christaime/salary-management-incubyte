package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.application.dto.employee.EmployeeRefDto;
import com.salary.management.domain.model.*;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.enums.ReportWarningCode;
import com.salary.management.domain.model.values.*;
import com.salary.management.domain.repository.PayrollPeriodSnapshotRepository;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.embeddable.AmountsEmbeddable;
import com.salary.management.infrastructure.persistence.entity.*;
import com.salary.management.infrastructure.persistence.mapper.PayrollPeriodPersistenceMapper;
import com.salary.management.infrastructure.persistence.projection.PayrollAggregateRow;
import com.salary.management.infrastructure.persistence.repository.*;
import com.salary.management.infrastructure.persistence.specification.PayrollSummarySpecification;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollPeriodSnapshotRepositoryAdapter implements PayrollPeriodSnapshotRepository {

    private static final int RANKING_LIMIT = 5;
    private static final int MAX_EMPLOYEES_IN_DETAILS = 5;
    private final PayrollSummaryJpaRepository payrollSummaryJpaRepository;
    private final EmployeePeriodicAssignmentJpaRepository employeePeriodicAssignmentJpaRepository;
    private final PayrollPeriodAggregateJpaRepository payrollPeriodAggregateJpaRepository;
    private final CurrencyRateJpaRepository currencyRateJpaRepository;

    private final DynamicRankingBuilder dynamicRankingBuilder;
    private final PayrollPeriodPersistenceMapper periodPersistenceMapper;
    private final PayrollPeriodEarnerPercentileJpaRepository earnerPercentileJpaRepository;
    private final PayrollPeriodEarnerRankingJpaRepository earnerRankingJpaRepository;
    private final PayrollPeriodEarnerRankingJdbcRepository jdbcRepository;

    @Override
    public PayrollPeriodSnapshotResult buildAndSaveAggregates(PayrollPeriod period) {
        log.info("Building snapshot for period {} ({} .. {}, frequency={}, closed={})",
                period.getId(), period.getStartDate(), period.getEndDate(),
                period.getFrequency(), period.getClosed());

        List<ReportWarning> warnings = new ArrayList<>();
        List<ReportWarning> blockers = new ArrayList<>();
        // We first collect warnings on the main period
        collectCoverageWarnings(period, warnings, blockers);

        Optional<String> referenceCurrency = currencyRateJpaRepository.findReferenceCurrencyCode();
        if(referenceCurrency.isEmpty()){
            log.error("buildAndSaveAggregates Period {}: no reference currency configured; skipping FX conversion for all remaining groups", period.getId());
            blockers.add(new ReportWarning(
                    ReportWarningCode.MISSING_REFERENCE_CURRENCY,
                    ReportWarning.Severity.BLOCKER,
                    "No reference currency defined ",
                    Map.of()));
            return PayrollPeriodSnapshotResult.empty();
        }

        String referenceCurrencyCode = referenceCurrency.get();
        List<PayrollPeriodAggregateEntity> aggregates = buildAggregates(period, referenceCurrencyCode, blockers);
        saveAggregates(period.getId(),aggregates);
        return new PayrollPeriodSnapshotResult(warnings,blockers);
    }

    @Override
    public PayrollPeriodSnapshotResult buildAndSaveRankings(PayrollPeriod period) {
        log.info("Building snapshot for period {} ({} .. {}, frequency={}, closed={})",
                period.getId(), period.getStartDate(), period.getEndDate(),
                period.getFrequency(), period.getClosed());

        List<ReportWarning> warnings = new ArrayList<>();
        List<ReportWarning> blockers = new ArrayList<>();
        // We first collect warnings on the main period
        collectCoverageWarnings(period, warnings, blockers);

        Optional<String> referenceCurrency = currencyRateJpaRepository.findReferenceCurrencyCode();
        if(referenceCurrency.isEmpty()){
            log.error("buildAndSaveRankings Period {}: no reference currency configured; skipping FX conversion for all remaining groups", period.getId());
            blockers.add(new ReportWarning(
                    ReportWarningCode.MISSING_REFERENCE_CURRENCY,
                    ReportWarning.Severity.BLOCKER,
                    "No reference currency defined ",
                    Map.of()));
            return PayrollPeriodSnapshotResult.empty();
        }

        String referenceCurrencyCode = referenceCurrency.get();
        log.debug("Ranking VALIDATED summaries for period {} (reference currency={}, top/bottom={})", period.getId(), referenceCurrencyCode, RANKING_LIMIT);
        List<PayrollPeriodEarnerRankingEntity> rankings = new ArrayList<>();
        List<PayrollPeriodEarnerPercentileEntity> percentiles = new ArrayList<>();
        appendRankings(period, referenceCurrencyCode, null, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.COUNTRY, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.DEPARTMENT, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.ROLE, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.EMPLOYMENT_TYPE, rankings, percentiles);
        log.debug("Period {}: {} ranking(s), {} percentile(s) collected", period.getId(), rankings.size(), percentiles.size());

        saveEarnerRankings(period.getId(),rankings,percentiles);
        return new PayrollPeriodSnapshotResult(warnings,blockers);
    }

    @Override
    public PayrollPeriodSnapshotResult buildSaveAggregatesAndRankings(PayrollPeriod period) {
        log.info("Building all snapshot for period {} ({} .. {}, frequency={}, closed={})",
                period.getId(), period.getStartDate(), period.getEndDate(),
                period.getFrequency(), period.getClosed());

        List<ReportWarning> warnings = new ArrayList<>();
        List<ReportWarning> blockers = new ArrayList<>();
        // We first collect warnings on the main period
        collectCoverageWarnings(period, warnings, blockers);

        Optional<String> referenceCurrency = currencyRateJpaRepository.findReferenceCurrencyCode();
        if(referenceCurrency.isEmpty()){
            log.error("buildSaveAggregatesAndRankings Period {}: no reference currency configured; skipping FX conversion for all remaining groups", period.getId());
            blockers.add(new ReportWarning(
                    ReportWarningCode.MISSING_REFERENCE_CURRENCY,
                    ReportWarning.Severity.BLOCKER,
                    "No reference currency defined ",
                    Map.of()));
            return PayrollPeriodSnapshotResult.empty();
        }

        // BUild and save agragates
        log.error("buildAndSaveAggregates Period {}", period.getId());
        String referenceCurrencyCode = referenceCurrency.get();
        List<PayrollPeriodAggregateEntity> aggregates = buildAggregates(period, referenceCurrencyCode, blockers);
        saveAggregates(period.getId(),aggregates);

        // Build and save rankings ans percentiles
        log.error("buildAndSaveRankings Period {}", period.getId());
        List<PayrollPeriodEarnerRankingEntity> rankings = new ArrayList<>();
        List<PayrollPeriodEarnerPercentileEntity> percentiles = new ArrayList<>();
        appendRankings(period, referenceCurrencyCode, null, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.COUNTRY, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.DEPARTMENT, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.ROLE, rankings, percentiles);
        appendRankings(period, referenceCurrencyCode, GroupKey.EMPLOYMENT_TYPE, rankings, percentiles);
        saveEarnerRankings(period.getId(),rankings,percentiles);

        return new PayrollPeriodSnapshotResult(warnings,blockers);
    }

    @Transactional
    private void saveAggregates(UUID periodId, List<PayrollPeriodAggregateEntity> aggregates){
        log.debug("Persisting aggregate snapshot for periodId={} — flush+clear pending persistence context",
                periodId);

        int aggregatesDeleted    = payrollPeriodAggregateJpaRepository.deleteDraftsByPayrollPeriodId(periodId);

        log.debug("Persisting aggregate snapshot for periodId={} — deleted DRAFT rows:  aggregates={}",periodId, aggregatesDeleted);
        try {
            payrollPeriodAggregateJpaRepository.saveAll(aggregates);
        } catch (RuntimeException ex) {
            log.error("Persisting snapshot for periodId={} failed: aggregates={}",
                    periodId, aggregates.size(),ex);
            throw ex;
        }

    }

    @Transactional
    private void saveEarnerRankings(UUID periodId, List<PayrollPeriodEarnerRankingEntity> rankings , List<PayrollPeriodEarnerPercentileEntity> percentiles){
        log.debug("Persisting ranking snapshot for periodId={} — flush+clear pending persistence context",
                periodId);

        int rankingsDeleted    = earnerRankingJpaRepository.deleteDraftsByPayrollPeriodId(periodId);
        int percentilesDeleted    = earnerPercentileJpaRepository.deleteDraftsByPayrollPeriodId(periodId);

        log.debug("Persisting ranking snapshot for periodId={} — deleted DRAFT rows:  rankings={}, percentiles={}",periodId, rankingsDeleted, percentilesDeleted);
        try {
            jdbcRepository.saveAll(rankings);
            jdbcRepository.saveAllPercentiles(percentiles);
        } catch (RuntimeException ex) {
            log.error("Persisting snapshot for periodId={} failed: rankings={}, percentiles={}",
                    periodId, rankings.size(), percentiles.size(),ex);
            throw ex;
        }

    }

    @Override
    public PayrollPeriodSnapshotResult checkCoverage(PayrollPeriod period) {
        log.debug("Checking coverage for period {} ({} .. {})",
                period.getId(), period.getStartDate(), period.getEndDate());
        List<ReportWarning> warnings = new ArrayList<>();
        List<ReportWarning> blockers = new ArrayList<>();
        collectCoverageWarnings(period, warnings, blockers);
        return new PayrollPeriodSnapshotResult(warnings, blockers);
    }

    private void collectCoverageWarnings(PayrollPeriod period, List<ReportWarning> warnings, List<ReportWarning> blockers) {
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
                    "Some employees with an in-effect assignment for '"+period.getName()+"' have no payroll summary",
                    Map.of( "period",period.getName(),
                            "count", missingCount[0],
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
                    "Some employees have a non-VALIDATED payroll summary in '"+period.getName()+"' period",
                    Map.of( "period",period.getName(),
                            "count", unvalidatedCount[0],
                            "employees", unvalidated)));
        } else {
            log.debug("Period {}: no unvalidated payroll summaries", period.getId());
        }
    }

    private List<EmployeeRefDto> findMissingEmployees(PayrollPeriod period, int[] countOut) {

        Page<PayrollSummaryEntity> page = employeePeriodicAssignmentJpaRepository.findMissingSummaryPage(
                period.getStartDate(), period.getEndDate(),
                null, null,
                null, null,
                null, null,
                PageRequest.of(0, MAX_EMPLOYEES_IN_DETAILS ));
        countOut[0] = (int) page.getTotalElements();
        List<EmployeeRefDto> details = new ArrayList<>();
        for (PayrollSummaryEntity summary : page.getContent()) {
            details.add(new EmployeeRefDto(summary.getEmployee().getId(), summary.getEmployee().getMatricule(), summary.getEmployee().getName()));
        }
        return details;
    }

    private List<EmployeeRefDto> findUnvalidatedEmployees(PayrollPeriod period, int[] countOut) {

        Page<PayrollSummaryEntity> page = payrollSummaryJpaRepository.findAll(
                PayrollSummarySpecification.build(PayrollSummaryCriteria.builder()
                        .rangeStart(period.getStartDate())
                        .rangeEnd(period.getEndDate())
                        .status(PayrollSummaryStatus.DRAFT)
                        .build()),
                PageRequest.of(0, MAX_EMPLOYEES_IN_DETAILS ));
        countOut[0] = (int) page.getTotalElements();
        List<EmployeeRefDto> details = new ArrayList<>();
        for (PayrollSummaryEntity summary : page.getContent()) {
            details.add(new EmployeeRefDto(summary.getEmployee().getId(), summary.getEmployee().getMatricule(), summary.getEmployee().getName()));
        }
        return details;
    }

    // ------------------------------------------------------------------
    // Aggregates
    // ------------------------------------------------------------------

    private List<PayrollPeriodAggregateEntity> buildAggregates(
            PayrollPeriod period, String referenceCurency, List<ReportWarning> blockers) {
        log.debug("Aggregating VALIDATED summaries for period {}", period.getId());

        List<PayrollAggregateRow> groups = payrollPeriodAggregateJpaRepository.aggregateInReferenceCurrency(period.getStartDate(), period.getEndDate(), referenceCurency);
        log.debug("Period {}: {} aggregate group(s) returned by repository", period.getId(), groups.size());

        List<PayrollPeriodAggregateEntity> aggregates = new ArrayList<>();
        Map<String,ReportWarning> missingFxReportWarning = new HashMap<>();

        for (PayrollAggregateRow g : groups) {
            AmountsEmbeddable original = getOriginalAmounts(g);
            AmountsEmbeddable reference = getReferenceAmounts(g);

            if(BigDecimal.ZERO.compareTo(original.getTotal()) < 0 && BigDecimal.ZERO.compareTo(reference.getTotal()) == 0){
                log.warn("Period {}: no FX rate for currency {}; reference amounts will be null for this group",  period.getId(), original.getCurrency());
                missingFxReportWarning.put(original.getCurrency(),
                    new ReportWarning(
                        ReportWarningCode.MISSING_FX_RATE,
                        ReportWarning.Severity.BLOCKER,
                        "No FX rate for currency " +original.getCurrency(),
                        Map.of("currency", original.getCurrency())));
            }

            aggregates.add(PayrollPeriodAggregateEntity.builder()
                    .id(UUID.randomUUID())
                    .payrollPeriodId(period.getId())
                    .status(PayrollSummaryStatus.DRAFT)
                    .countryId(g.getCountryId())
                    .departmentId(g.getDepartmentId())
                    .roleId(g.getRoleId())
                    .employmentType(g.getEmploymentType())
                    .originalAmounts(original)
                    .referenceAmounts(reference)
                    .employeeCount(g.getEmployeeCount().intValue())
                    .build());
        }
        blockers.addAll(missingFxReportWarning.values());
        log.debug("Period {}: built {} aggregate(s){}",
                period.getId(), aggregates.size(),
                !missingFxReportWarning.isEmpty()
                        ? " (" + missingFxReportWarning.size() + " group(s) without FX rate)"
                        : "");

        return aggregates;
    }

    private AmountsEmbeddable getOriginalAmounts(PayrollAggregateRow g){
        BigDecimal base = AmountUtils.scaleNullToZero(g.getBase());
        BigDecimal variable =  AmountUtils.scaleNullToZero(g.getVariable());
        BigDecimal allowances =  AmountUtils.scaleNullToZero(g.getAllowances());
        BigDecimal employerTaxes =  AmountUtils.scaleNullToZero(g.getEmployerTaxes());
       return  AmountsEmbeddable.builder()
                .base(base)
                .variable(variable)
                .allowances(allowances)
                .employerTaxes(employerTaxes)
                .total(base.add(variable).add(allowances).add(employerTaxes))
                .currency(g.getCurrency())
                .build();
    }

    private AmountsEmbeddable getReferenceAmounts(PayrollAggregateRow g){
        BigDecimal base = AmountUtils.scaleNullToZero(g.getReferenceBase());
        BigDecimal variable =  AmountUtils.scaleNullToZero(g.getReferenceVariable());
        BigDecimal allowances =  AmountUtils.scaleNullToZero(g.getReferenceAllowances());
        BigDecimal employerTaxes =  AmountUtils.scaleNullToZero(g.getReferenceEmployerTaxes());
        return  AmountsEmbeddable.builder()
                .base(base)
                .variable(variable)
                .allowances(allowances)
                .employerTaxes(employerTaxes)
                .total(base.add(variable).add(allowances).add(employerTaxes))
                .currency(g.getReferenceCurrency())
                .build();
    }
    // ------------------------------------------------------------------
    // Rankings
    // ------------------------------------------------------------------

    /**
     * Runs {@code rankAndPercentile} for one group key and appends its results.
     * Extracted to remove the five-times duplication of the null-check dance.
     */
    private void appendRankings(PayrollPeriod period,
                                String referenceCurrency,
                                GroupKey groupKey,
                                List<PayrollPeriodEarnerRankingEntity> rankings,
                                List<PayrollPeriodEarnerPercentileEntity> percentiles) {
        var result = dynamicRankingBuilder.rankAndComputePercentilesForPeriod(
                         periodPersistenceMapper.toEntity(period), referenceCurrency, groupKey,RANKING_LIMIT);

        if (result == null) {
            log.warn("Period {}: ranking repository returned null for group {}",
                    period.getId(), groupKey!=null ? groupKey.name() : "GLOBAL");
            return;
        }

        int r = result.rankings() == null ? 0 : result.rankings().size();
        int p = result.percentiles() == null ? 0 : result.percentiles().size();
        log.trace("Period {}: group {} produced {} ranking(s) and {} percentile(s)",
                period.getId(), groupKey!=null ? groupKey.name() : "GLOBAL", r, p);

        if (result.rankings() != null) {
            rankings.addAll(result.rankings());
        }
        if (result.percentiles() != null) {
            percentiles.addAll(result.percentiles());
        }
    }
}
