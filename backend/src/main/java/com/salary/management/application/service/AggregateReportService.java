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
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.model.values.SnapshotBuildResult;
import com.salary.management.domain.repository.*;
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
import java.util.Objects;
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

        Map<GroupTuple, StatisticsPair> statistics = request.includeStatistics()
                ? buildStatistics(reportDataRepository.queryStatistics(
                        periodIds, request.groupKeys(), filters),
                request.groupKeys())
                : Map.of();

        List<AggregateLineDto> lines =
                assembleLines(aggregateRows, statistics, request);

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
        if (request.includeStatistics()) {
            Set<GroupKey> allowed = Set.of(
                    GroupKey.COUNTRY, GroupKey.DEPARTMENT,
                    GroupKey.ROLE, GroupKey.EMPLOYMENT_TYPE);
            for (GroupKey key : request.groupKeys()) {
                if (!allowed.contains(key)) {
                    errors.add(new ValidationError(
                            "groupKeys", "StatisticsGroupKey",
                            "includeStatistics supports only COUNTRY, DEPARTMENT, "
                                    + "ROLE, EMPLOYMENT_TYPE; got " + key, Map.of()));
                }
            }
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

    private Map<GroupTuple, StatisticsPair> buildStatistics(
            List<AggregateRowProjection> rows,
            List<GroupKey> groupKeys) {
        Map<GroupTuple, StatisticsPair> map = new LinkedHashMap<>();
        for (AggregateRowProjection row : rows) {
            GroupTuple key = rowToKey(row, groupKeys);
            StatisticsPair pair = map.computeIfAbsent(key, k -> new StatisticsPair());
            if (EarnerRankingDirection.TOP.equals(row.direction)) {
                pair.max = row.amount;
            } else if (EarnerRankingDirection.BOTTOM.equals(row.direction)) {
                pair.min = row.amount;
            }
        }
        return map;
    }

    private List<AggregateLineDto> assembleLines(
            List<AggregateRowProjection> rows,
            Map<GroupTuple, StatisticsPair> statistics,
            AggregateReportRequest request) {
        List<AggregateLineDto> lines = new ArrayList<>(rows.size());
        for (AggregateRowProjection row : rows) {
            GroupTuple key = rowToKey(row, request.groupKeys());
            AmountDto amount = new AmountDto(row.sumTotal, referenceCurrency);
            AmountsDto breakdown = request.breakdownRequested()
                    ? new AmountsDto(
                    row.sumBase, row.sumVariable, row.sumAllowances,
                    row.sumEmployerTaxes, row.sumTotal, referenceCurrency)
                    : null;
            StatisticsDto stats = null;
            if (request.includeStatistics()) {
                StatisticsPair pair = statistics.get(key);
                BigDecimal average = row.sumEmployeeCount != null && row.sumEmployeeCount > 0
                        ? row.sumTotal.divide(
                        BigDecimal.valueOf(row.sumEmployeeCount),
                        2, RoundingMode.HALF_UP)
                        : null;
                AmountDto min = pair != null && pair.min != null
                        ? new AmountDto(pair.min, referenceCurrency) : null;
                AmountDto max = pair != null && pair.max != null
                        ? new AmountDto(pair.max, referenceCurrency) : null;
                AmountDto avg = average != null
                        ? new AmountDto(average, referenceCurrency) : null;
                stats = new StatisticsDto(min, max, avg);
            }
            lines.add(new AggregateLineDto(
                    amount,
                    row.sumEmployeeCount == null ? 0 : row.sumEmployeeCount.intValue(),
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

    private GroupTuple rowToKey(AggregateRowProjection row, List<GroupKey> groupKeys) {
        GroupTuple key = new GroupTuple();
        for (GroupKey k : groupKeys) {
            switch (k) {
                case COUNTRY -> key.values.put(k, row.country);
                case DEPARTMENT -> key.values.put(k, row.department);
                case ROLE -> key.values.put(k, row.role);
                case EMPLOYMENT_TYPE -> key.values.put(k, row.employmentType);
                case CURRENCY -> key.values.put(k, row.currency);
                default -> throw new IllegalStateException("Unsupported group key: " + k);
            }
        }
        return key;
    }

    private static final class GroupTuple {
        private final Map<GroupKey, Object> values = new LinkedHashMap<>();

        @Override
        public boolean equals(Object o) {
            return o instanceof GroupTuple g && values.equals(g.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(values);
        }
    }

    private static final class StatisticsPair {
        private BigDecimal min;
        private BigDecimal max;
    }
}