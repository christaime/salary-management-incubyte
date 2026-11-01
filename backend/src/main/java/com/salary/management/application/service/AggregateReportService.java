package com.salary.management.application.service;

import com.salary.management.application.dto.aggregate.AggregateLineDto;
import com.salary.management.application.dto.aggregate.AggregateReportRequest;
import com.salary.management.application.dto.aggregate.AggregateReportResponse;
import com.salary.management.application.dto.AmountDto;
import com.salary.management.application.dto.AmountsDto;
import com.salary.management.application.dto.DateRange;
import com.salary.management.application.dto.aggregate.StatisticsDto;
import com.salary.management.application.dto.aggregate.StatisticsPerFrequency;
import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.values.AmountUtils;
import com.salary.management.domain.model.values.PayrollPeriodSnapshotResult;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.repository.*;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.domain.repository.criteria.ReportEffectiveFilters;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Generates the aggregate payroll report defined in Milestone 5. */
@Slf4j
@Service
@AllArgsConstructor
public class AggregateReportService {

    private final ReportDataRepository reportDataRepository;
    private final CurrencyRateRepository currencyRateRepository;
    protected final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollPeriodSnapshotRepository payrollPeriodSnapshotRepository;


    protected String referenceCurrency() {
        return currencyRateRepository.getReferenceCurrencyCode();
    }

    /**
     * Generates the aggregate report for the given request.
     *
     * @param request report request
     * @return aggregate report response
     * @throws DomainValidationException when the request is invalid
     * @throws ReportBlockedException    when a snapshot build returns a blocker
     */
    @Transactional
    public AggregateReportResponse generate(AggregateReportRequest request) {
        log.info("Aggregate report requested: range={}..{}, groupKeys={}, "
                        + "includeStatistics={}, breakdown={}, filters={}",
                request.dateRange().startDate(), request.dateRange().endDate(),
                request.groupKeys(), request.includeStatistics(),
                request.breakdownRequested(), describeFilters(request));

        String referenceCurrency = referenceCurrency();

        ReportEffectiveFilters filters = validate(request);
        List<PayrollPeriod> periods = resolvePeriods(request.dateRange());
        if (periods.isEmpty()) {
            log.info("No payroll periods in range {}..{}; returning empty report",
                    request.dateRange().startDate(), request.dateRange().endDate());
            return AggregateReportResponse.of(request, referenceCurrency);
        }
        log.debug("Resolved {} payroll period(s) in range", periods.size());

        List<PayrollPeriod> toGenerate = periods.stream()
                .filter(p -> !p.getClosed())
                .toList();
        log.debug("Rebuilding snapshots for {} open period(s) of {} total",
                toGenerate.size(), periods.size());

        List<ReportWarning> collectedWarnings = new ArrayList<>();
        for (PayrollPeriod period : toGenerate) {
            log.debug("Building snapshot for periodId={}", period.getId());
            PayrollPeriodSnapshotResult result = payrollPeriodSnapshotRepository.buildAndSaveAggregates(period);
            collectedWarnings.addAll(result.warnings());
            if (result.isBlocked()) {
                log.warn("Snapshot build for periodId={} returned {} blocker(s); "
                                + "aborting report generation",
                        period.getId(), result.blockers().size());
                throw new ReportBlockedException(result.blockers(), collectedWarnings);
            }
        }

        List<UUID> periodIds = periods.stream().map(PayrollPeriod::getId).toList();
        List<AggregateRowProjection> aggregateRows = reportDataRepository.queryAggregates(
                periodIds, request.groupKeys(), filters);
        log.debug("Aggregate query returned {} row(s)", aggregateRows.size());

        GroupKey statisticsKey = request.includeStatistics()
                ? singleKeyOf(request.groupKeys())
                : null;

        List<AggregateRowProjection> monthlyStatistics = List.of();
        List<AggregateRowProjection> biWeeklyStatistics = List.of();
        if (request.includeStatistics()) {
            monthlyStatistics = reportDataRepository.queryStatistics(
                            request.dateRange().startDate(), request.dateRange().endDate(), request.groupKeys(),
                            PayrollPeriodFrequency.MONTHLY, filters, referenceCurrency);
            biWeeklyStatistics = reportDataRepository.queryStatistics(
                            request.dateRange().startDate(), request.dateRange().endDate(), request.groupKeys(),
                            PayrollPeriodFrequency.BI_WEEKLY, filters, referenceCurrency);
            log.debug("Statistics: monthly={} partition(s), biWeekly={} partition(s)",
                    monthlyStatistics.size(), biWeeklyStatistics.size());
        }

        List<AggregateLineDto> lines = assembleLines(
                aggregateRows, monthlyStatistics, biWeeklyStatistics, statisticsKey, request);

        log.info("Aggregate report generated: {} line(s), refCurrency={}, "
                        + "{} warning(s), {} period(s) in range",
                lines.size(), referenceCurrency, collectedWarnings.size(), periods.size());

        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                request.dateRange(),
                request.groupKeys(),
                referenceCurrency,
                lines,
                collectedWarnings);
    }

    private ReportEffectiveFilters validate(AggregateReportRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        DateRange range = request.dateRange();
        if (range.startDate().isAfter(range.endDate())) {
            errors.add(new ValidationError(
                    "dateRange", "DateOrder",
                    "startDate must not be after endDate", Map.of()));
        }
        if (range.endDate().isAfter(LocalDate.now())) {
            errors.add(new ValidationError(
                    "dateRange", "FutureDate",
                    "endDate must not be in the future", Map.of()));
        }
        Set<GroupKey> distinct = new HashSet<>(request.groupKeys());
        if (distinct.size() != request.groupKeys().size()) {
            errors.add(new ValidationError(
                    "groupKeys", "Duplicate",
                    "groupKeys must not contain duplicates", Map.of()));
        }
        if (request.groupKeys().contains(GroupKey.ORIGIN_COUNTRY)) {
            errors.add(new ValidationError(
                    "groupKeys", "Unsupported",
                    "ORIGIN_COUNTRY is not supported as a group key", Map.of()));
        }
        if (request.includeStatistics() && request.groupKeys().size() > 1) {
            errors.add(new ValidationError(
                    "groupKeys", "StatisticsSingleDimension",
                    "includeStatistics supports at most one group key", Map.of()));
        }

        if (!errors.isEmpty()) {
            log.warn("Aggregate report rejected: {} validation error(s): {}",
                    errors.size(), errors);
            throw new DomainValidationException(errors);
        }
        return effectiveFilters(request);
    }

    private ReportEffectiveFilters effectiveFilters(AggregateReportRequest request) {
        return new ReportEffectiveFilters(
                request.anyCountryIds(),
                request.anyDepartmentIds(),
                request.anyRoleIds(),
                request.anyEmploymentTypes());
    }

    private List<PayrollPeriod> resolvePeriods(DateRange range) {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .startDate(range.startDate())
                .endDate(range.endDate())
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .size(Integer.MAX_VALUE)
                .build();
        return payrollPeriodRepository.list(criteria);
    }

    // ------------------------------------------------------------------
    // Statistics
    // ------------------------------------------------------------------

    private AggregateRowProjection statisticsOf(AggregateRowProjection aggregate, GroupKey key, List<AggregateRowProjection> statistics) {
        if (key == null) {
            return statistics.stream()
                    .filter(s-> s.country == null && s.department == null && s.role == null && s.employmentType == null)
                    .findFirst().orElse(null);
        }

        return switch (key) {
            case COUNTRY -> statistics.stream()
                    .filter(s -> s.country != null && aggregate.country != null
                            && s.country.id().equals(aggregate.country.id())
                            && s.department == null && s.role == null && s.employmentType == null)
                    .findFirst().orElse(null);
            case DEPARTMENT -> statistics.stream()
                    .filter(s -> s.department != null && aggregate.department != null
                            && s.department.id().equals(aggregate.department.id())
                            && s.country == null && s.role == null && s.employmentType == null)
                    .findFirst().orElse(null);
            case ROLE -> statistics.stream()
                    .filter(s -> s.role != null && aggregate.role != null
                            && s.role.id().equals(aggregate.role.id())
                            && s.country == null && s.department == null && s.employmentType == null)
                    .findFirst().orElse(null);
            case EMPLOYMENT_TYPE -> statistics.stream()
                    .filter(s -> s.employmentType != null && aggregate.employmentType != null
                            && s.employmentType.equals(aggregate.employmentType)
                            && s.country == null && s.department == null && s.role == null)
                    .findFirst().orElse(null);
            case CURRENCY, ORIGIN_COUNTRY -> throw new IllegalArgumentException("Unsupported statistics key: " + key);
        };
    }

    private GroupKey singleKeyOf(List<GroupKey> groupKeys) {
        if (groupKeys.isEmpty()) {
            return null;
        }
        if (groupKeys.size() == 1) {
            return groupKeys.get(0);
        }
        throw new IllegalStateException(
                "Statistics are only available for a single dimension; got " + groupKeys);
    }

    // ------------------------------------------------------------------
    // Lines
    // ------------------------------------------------------------------

    private List<AggregateLineDto> assembleLines(
            List<AggregateRowProjection> rows,
            List<AggregateRowProjection> monthlyStatistics,
            List<AggregateRowProjection> biWeeklyStatistics,
            GroupKey statisticsKey,
            AggregateReportRequest request) {
        List<AggregateLineDto> lines = new ArrayList<>(rows.size());
        String referenceCurrency = referenceCurrency();
        for (AggregateRowProjection row : rows) {
            AmountDto amount = new AmountDto(row.sumTotal, referenceCurrency);
            AmountsDto breakdown = request.breakdownRequested()
                    ? new AmountsDto(
                    row.sumBase, row.sumVariable, row.sumAllowances,
                    row.sumEmployerTaxes, row.sumTotal, referenceCurrency)
                    : null;
            StatisticsDto stats = null;
            if (request.includeStatistics()) {
                AggregateRowProjection stat = statisticsOf(row, statisticsKey, monthlyStatistics);
                StatisticsPerFrequency monthly = stat != null? toPerFrequency(
                        stat.minReferenceAmount, stat.maxReferenceAmount,
                        stat.monthlyTotal, stat.monthlyEmployeeCount, stat.monthlyPeriodCount) : null;
                stat = statisticsOf(row, statisticsKey, biWeeklyStatistics);
                StatisticsPerFrequency biWeekly = stat != null ? toPerFrequency(
                        stat.minReferenceAmount, stat.maxReferenceAmount,
                        stat.biWeeklyTotal, stat.biWeeklyEmployeeCount, stat.biWeeklyPeriodCount) : null;
                stats = new StatisticsDto(monthly, biWeekly);
                BigDecimal totalAmount = monthly != null && monthly.total() != null ? monthly.total().amount() : BigDecimal.ZERO;
                if(biWeekly != null && biWeekly.total() != null) {
                    totalAmount = totalAmount.add(biWeekly.total().amount());// The amount is the sum of stat result if stats present
                }
                amount = new AmountDto(totalAmount, referenceCurrency);
            }
            lines.add(new AggregateLineDto(
                    amount,
                    row.employeeCount == null ? 0 : row.employeeCount.intValue(),
                    row.country,
                    row.department,
                    row.role,
                    row.employmentType,
                    row.currency,
                    breakdown,
                    stats));
        }
        return lines;
    }

    private StatisticsPerFrequency toPerFrequency(
            BigDecimal minAmount, BigDecimal maxAmount,
            BigDecimal total,
            Long employeeCount,
            Long periodCount) {

        int employees = employeeCount == null ? 0 : employeeCount.intValue();
        int periods = periodCount == null ? 0 : periodCount.intValue();
        BigDecimal totalAmount = total == null ? java.math.BigDecimal.ZERO : total;
        String referenceCurrency = referenceCurrency();
        AmountDto min = minAmount != null
                ? new AmountDto(minAmount.setScale(
                AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency)
                : null;
        AmountDto max = maxAmount != null
                ? new AmountDto(maxAmount.setScale(
                AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency)
                : null;
        AmountDto average = employees > 0
                ? new AmountDto(totalAmount.divide(
                BigDecimal.valueOf(employees).multiply(BigDecimal.valueOf(periods)),
                AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency)
                : null;
        AmountDto totalDto = new AmountDto(
                totalAmount.setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency);
        return new StatisticsPerFrequency(min, max, average, totalDto, employees, periods);
    }

    // ------------------------------------------------------------------
    // Logging helpers
    // ------------------------------------------------------------------

    /** Compact filter description, for log lines that would otherwise be huge. */
    private String describeFilters(AggregateReportRequest request) {
        return "{countries=" + sizeOf(request.anyCountryIds())
                + ", departments=" + sizeOf(request.anyDepartmentIds())
                + ", roles=" + sizeOf(request.anyRoleIds())
                + ", employmentTypes=" + sizeOf(request.anyEmploymentTypes()) + "}";
    }

    private int sizeOf(Collection<?> list) {
        return list == null ? 0 : list.size();
    }

}