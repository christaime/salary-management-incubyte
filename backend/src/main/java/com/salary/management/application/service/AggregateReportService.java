package com.salary.management.application.service;

import com.salary.management.application.dto.AggregateLineDto;
import com.salary.management.application.dto.AggregateReportRequest;
import com.salary.management.application.dto.AggregateReportResponse;
import com.salary.management.application.dto.AmountDto;
import com.salary.management.application.dto.AmountsDto;
import com.salary.management.application.dto.DateRange;
import com.salary.management.application.dto.StatisticsDto;
import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.application.snapshot.SnapshotBuilder;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.values.AmountUtils;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.model.values.SnapshotBuildResult;
import com.salary.management.domain.repository.CurrencyRateRepository;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.ReportDataRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.domain.repository.criteria.ReportEffectiveFilters;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Generates the aggregate payroll report defined in Milestone 5. */
@Service
@RequiredArgsConstructor
public class AggregateReportService {

    private final PayrollPeriodRepository payrollPeriodRepository;
    private final PayrollPeriodAggregateRepository payrollPeriodAggregateRepository;
    private final PayrollPeriodEarnerRankingRepository payrollPeriodEarnerRankingRepository;
    private final ReportDataRepository reportDataRepository;
    private final SnapshotBuilder snapshotBuilder;
    private final CurrencyRateRepository currencyRateRepository;
    private String referenceCurrency;

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
        referenceCurrency = currencyRateRepository.getReferenceCurrencyCode();

        ReportEffectiveFilters filters = validate(request);
        List<PayrollPeriod> periods = resolvePeriods(request.dateRange());
        if (periods.isEmpty()) {
            return emptyResponse(request);
        }

        List<PayrollPeriod> toGenerate = periods.stream()
                .filter(p -> !p.getClosed())
                .toList();
        List<ReportWarning> warnings = buildAndPersistSnapshots(toGenerate);

        List<UUID> periodIds = periods.stream().map(PayrollPeriod::getId).toList();
        List<AggregateRowProjection> aggregateRows = reportDataRepository.queryAggregates(
                periodIds, request.groupKeys(), filters);

        GroupKey statisticsKey = request.includeStatistics()
                ? singleKeyOf(request.groupKeys())
                : null;

        Map<StatisticsKey, StatisticsPair> statistics = request.includeStatistics() && request.groupKeys().size() <= 1
                ? buildStatistics(reportDataRepository.queryStatistics(periodIds, request.groupKeys(), filters, referenceCurrency), statisticsKey)
                : Map.of();

        List<AggregateLineDto> lines = assembleLines(aggregateRows, statistics, statisticsKey, request);

        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                request.dateRange(),
                request.groupKeys(),
                referenceCurrency,
                lines,
                warnings);
    }

    private AggregateReportResponse emptyResponse(AggregateReportRequest request) {
        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                request.dateRange(),
                request.groupKeys(),
                referenceCurrency,
                List.of(),
                List.of());
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
            throw new DomainValidationException(errors);
        }
        return effectiveFilters(request);
    }

    private ReportEffectiveFilters effectiveFilters(AggregateReportRequest request) {
        Set<GroupKey> keys = Set.copyOf(request.groupKeys());
        return new ReportEffectiveFilters(
                keys.contains(GroupKey.COUNTRY) ? null : request.anyCountryIds(),
                keys.contains(GroupKey.DEPARTMENT) ? null : request.anyDepartmentIds(),
                keys.contains(GroupKey.ROLE) ? null : request.anyRoleIds(),
                keys.contains(GroupKey.EMPLOYMENT_TYPE) ? null : request.anyEmploymentTypes());
    }

    private List<PayrollPeriod> resolvePeriods(DateRange range) {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .startDate(range.startDate())
                .endDate(range.endDate())
                .size(Integer.MAX_VALUE)
                .build();
        return payrollPeriodRepository.list(criteria);
    }

    private List<ReportWarning> buildAndPersistSnapshots(List<PayrollPeriod> periods) {
        if (periods.isEmpty()) {
            return List.of();
        }
        List<ReportWarning> collectedWarnings = new ArrayList<>();
        List<SnapshotBuildResult> built = new ArrayList<>();
        for (PayrollPeriod period : periods) {
            SnapshotBuildResult result = snapshotBuilder.build(period);
            collectedWarnings.addAll(result.warnings());
            if (!result.blockers().isEmpty()) {
                throw new ReportBlockedException(result.blockers(), collectedWarnings);
            }
            built.add(result);
        }
        for (int i = 0; i < periods.size(); i++) {
            UUID periodId = periods.get(i).getId();
            SnapshotBuildResult result = built.get(i);
            payrollPeriodAggregateRepository.deleteDraftsByPayrollPeriodId(periodId);
            payrollPeriodEarnerRankingRepository.deleteDraftsByPayrollPeriodId(periodId);
            payrollPeriodAggregateRepository.saveAll(result.aggregates());
            payrollPeriodEarnerRankingRepository.saveAll(result.rankings());
        }
        return collectedWarnings;
    }

    // ------------------------------------------------------------------
    // Statistics
    // ------------------------------------------------------------------

    /**
     * Builds the statistics map for one period. Throws if the query returned two rows for
     * the same key, which would indicate a bug in the query or the projection.
     */
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
            Map<StatisticsKey, StatisticsPair> statistics,
            GroupKey statisticsKey,
            AggregateReportRequest request) {
        List<AggregateLineDto> lines = new ArrayList<>(rows.size());
        for (AggregateRowProjection row : rows) {
            AmountDto amount = new AmountDto(row.sumTotal, referenceCurrency);
            AmountsDto breakdown = request.breakdownRequested()
                    ? new AmountsDto(
                    row.sumBase, row.sumVariable, row.sumAllowances,
                    row.sumEmployerTaxes, row.sumTotal, referenceCurrency)
                    : null;
            StatisticsDto stats = null;
            if (request.includeStatistics()) {
                StatisticsPair pair = statistics.get(keyOf(row, statisticsKey));
                AmountDto min = pair != null && pair.min() != null
                        ? new AmountDto(pair.min().setScale(
                        AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                        referenceCurrency)
                        : null;
                AmountDto max = pair != null && pair.max() != null
                        ? new AmountDto(pair.max().setScale(
                        AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                        referenceCurrency)
                        : null;
                AmountDto average = row.employeeCount != null && row.employeeCount > 0
                        ? new AmountDto(row.sumTotal.divide(
                        BigDecimal.valueOf(row.employeeCount),
                        AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP),
                        referenceCurrency)
                        : null;
                stats = new StatisticsDto(min, max, average);
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

    // ------------------------------------------------------------------
    // Small value types
    // ------------------------------------------------------------------

    /**
     * Key for the statistics map. Exactly one field is non-null for a single-dimension
     * partition, and all four are null for the global partition. The record's generated
     * {@code equals} and {@code hashCode} compare all four fields, so two keys are equal
     * only when they describe the same partition instance.
     */
    private record StatisticsKey(
            UUID countryId,
            UUID departmentId,
            UUID roleId,
            EmploymentType employmentType) {
    }

    /** Min and max reference amount for one partition instance. */
    private record StatisticsPair(BigDecimal min, BigDecimal max) {
    }
}