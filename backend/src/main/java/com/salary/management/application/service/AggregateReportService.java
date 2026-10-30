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
import com.salary.management.application.snapshot.SnapshotBuilder;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.values.AmountUtils;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.repository.CurrencyRateRepository;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.ReportDataRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.domain.repository.criteria.ReportEffectiveFilters;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import jakarta.persistence.EntityManager;
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
    private final ReportService baseService;


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
            return baseService.emptyResponse(request, referenceCurrency);
        }
        log.debug("Resolved {} payroll period(s) in range", periods.size());

        List<PayrollPeriod> toGenerate = periods.stream()
                .filter(p -> !p.getClosed())
                .toList();
        log.debug("Rebuilding snapshots for {} open period(s) of {} total",
                toGenerate.size(), periods.size());
        List<ReportWarning> warnings = baseService.buildAndPersistSnapshots(toGenerate);
        if (!warnings.isEmpty()) {
            log.info("Snapshot build produced {} warning(s): {}",
                    warnings.size(), warnings);
        }

        List<UUID> periodIds = periods.stream().map(PayrollPeriod::getId).toList();
        List<AggregateRowProjection> aggregateRows = reportDataRepository.queryAggregates(
                periodIds, request.groupKeys(), filters);
        log.debug("Aggregate query returned {} row(s)", aggregateRows.size());

        GroupKey statisticsKey = request.includeStatistics()
                ? singleKeyOf(request.groupKeys())
                : null;

        Map<StatisticsKey, StatisticsPair> monthlyStatistics = Map.of();
        Map<StatisticsKey, StatisticsPair> biWeeklyStatistics = Map.of();
        if (request.includeStatistics()) {
            monthlyStatistics = buildStatistics(
                    reportDataRepository.queryStatistics(
                            periodIds, request.groupKeys(),
                            PayrollPeriodFrequency.MONTHLY, filters, referenceCurrency),
                    statisticsKey);
            biWeeklyStatistics = buildStatistics(
                    reportDataRepository.queryStatistics(
                            periodIds, request.groupKeys(),
                            PayrollPeriodFrequency.BI_WEEKLY, filters, referenceCurrency),
                    statisticsKey);
            log.debug("Statistics: monthly={} partition(s), biWeekly={} partition(s)",
                    monthlyStatistics.size(), biWeeklyStatistics.size());
        }

        List<AggregateLineDto> lines = assembleLines(
                aggregateRows, monthlyStatistics, biWeeklyStatistics, statisticsKey, request);

        log.info("Aggregate report generated: {} line(s), refCurrency={}, "
                        + "{} warning(s), {} period(s) in range",
                lines.size(), referenceCurrency, warnings.size(), periods.size());

        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                request.dateRange(),
                request.groupKeys(),
                referenceCurrency,
                lines,
                warnings);
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

    private Map<StatisticsKey, StatisticsPair> buildStatistics(
            List<AggregateRowProjection> rows,
            GroupKey key) {
        Map<StatisticsKey, StatisticsPair> map = new LinkedHashMap<>();
        for (AggregateRowProjection row : rows) {
            StatisticsKey k = keyOf(row, key);
            if (map.containsKey(k)) {
                throw new IllegalStateException(
                        "Statistics query returned more than one row for key " + k);
            }
            map.put(k, new StatisticsPair(row.minReferenceAmount, row.maxReferenceAmount));
        }
        return map;
    }

    private StatisticsKey keyOf(AggregateRowProjection row, GroupKey key) {
        if (key == null) {
            return new StatisticsKey(null, null, null, null);
        }
        return switch (key) {
            case COUNTRY -> new StatisticsKey(row.country.id(), null, null, null);
            case DEPARTMENT -> new StatisticsKey(null, row.department.id(), null, null);
            case ROLE -> new StatisticsKey(null, null, row.role.id(), null);
            case EMPLOYMENT_TYPE -> new StatisticsKey(null, null, null, row.employmentType);
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported statistics key: " + key);
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
            Map<StatisticsKey, StatisticsPair> monthlyStatistics,
            Map<StatisticsKey, StatisticsPair> biWeeklyStatistics,
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
                StatisticsKey key = keyOf(row, statisticsKey);
                StatisticsPerFrequency monthly = toPerFrequency(
                        monthlyStatistics.get(key),
                        row.monthlyTotal, row.monthlyEmployeeCount, row.monthlyPeriodCount);
                StatisticsPerFrequency biWeekly = toPerFrequency(
                        biWeeklyStatistics.get(key),
                        row.biWeeklyTotal, row.biWeeklyEmployeeCount, row.biWeeklyPeriodCount);
                stats = new StatisticsDto(monthly, biWeekly);
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
            StatisticsPair pair,
            BigDecimal total,
            Long employeeCount,
            Long periodCount) {

        if (pair == null) return null;

        int employees = employeeCount == null ? 0 : employeeCount.intValue();
        int periods = periodCount == null ? 0 : periodCount.intValue();
        BigDecimal totalAmount = total == null ? BigDecimal.ZERO : total;
        String referenceCurrency = referenceCurrency();
        AmountDto min = pair.min() != null
                ? new AmountDto(pair.min().setScale(
                AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency)
                : null;
        AmountDto max = pair.max() != null
                ? new AmountDto(pair.max().setScale(
                AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency)
                : null;
        AmountDto average = employees > 0
                ? new AmountDto(totalAmount.divide(
                BigDecimal.valueOf(employees),
                AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency)
                : null;
        AmountDto totalDto = new AmountDto(
                totalAmount.setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                referenceCurrency);
        int averageEmployees = periods > 0 ? (int) Math.round((double) employees / periods) : 0;
        return new StatisticsPerFrequency(min, max, average, totalDto, averageEmployees, periods);
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

    // ------------------------------------------------------------------
    // Small value types
    // ------------------------------------------------------------------

    private record StatisticsKey(
            UUID countryId,
            UUID departmentId,
            UUID roleId,
            EmploymentType employmentType) {
    }

    private record StatisticsPair(BigDecimal min, BigDecimal max) {
    }
}