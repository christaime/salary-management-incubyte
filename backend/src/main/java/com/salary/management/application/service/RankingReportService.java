package com.salary.management.application.service;

import com.salary.management.application.dto.employee.EmployeeRefDto;
import com.salary.management.application.dto.PayrollPeriodResponse;
import com.salary.management.application.dto.RefDto;
import com.salary.management.application.dto.ranking.EarnerRankingDto;
import com.salary.management.application.dto.ranking.EarningRankingReportRequest;
import com.salary.management.application.dto.ranking.EarningRankingReportResponse;
import com.salary.management.application.dto.ranking.PositionContextDto;
import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.application.snapshot.SnapshotBuilder;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.*;
import com.salary.management.domain.model.enums.*;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Generates the earner ranking report for a single payroll period. */
@Slf4j
@Service
public class RankingReportService extends ReportService {

    private static final int BASIS_POINTS = 10000;
    private static final int PERCENTILE_STEP = 100;
    private static final int[] PERCENTILE_LABELS = {0, 10, 25, 50, 75, 90, 100};

    private final PayrollPeriodAggregateRepository aggregateRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final EmployeePeriodicAssignmentRepository periodicAssignmentRepository;

    public RankingReportService(
            PayrollPeriodRepository payrollPeriodRepository,
            PayrollPeriodAggregateRepository payrollPeriodAggregateRepository,
            PayrollPeriodEarnerRankingRepository payrollPeriodEarnerRankingRepository,
            SnapshotBuilder snapshotBuilder,
            PayrollPeriodAggregateRepository aggregateRepository,
            CurrencyRateRepository currencyRateRepository, EmployeePeriodicAssignmentRepository periodicAssignmentRepository) {
        super(payrollPeriodRepository,
                payrollPeriodAggregateRepository,
                payrollPeriodEarnerRankingRepository,
                snapshotBuilder);
        this.aggregateRepository = aggregateRepository;
        this.currencyRateRepository = currencyRateRepository;
        this.periodicAssignmentRepository = periodicAssignmentRepository;
    }

    @Override
    protected String referenceCurrency() {
        return currencyRateRepository.getReferenceCurrencyCode();
    }

    /**
     * Builds the ranking report for the requested period and dimension.
     *
     * @throws DomainValidationException when the request is invalid
     * @throws ReportBlockedException    when an open period's snapshot build
     *                                   returns a blocker
     */
    @Transactional
    public EarningRankingReportResponse generate(EarningRankingReportRequest request) {
        log.info("Ranking report requested: periodId={}, groupKey={}, dimension={}",
                request.periodId(), request.groupKey(), describeDimension(request));

        validate(request);

        PayrollPeriod period = payrollPeriodRepository
                .findById(request.periodId())
                .orElseThrow(() -> new DomainValidationException(List.of(
                        new ValidationError("periodId", "NotFound",
                                "Payroll period not found", Map.of()))));

        // Closed periods already hold a snapshot — read-only. Open periods get
        // a rebuild before the report is assembled.
        List<ReportWarning> warnings;
        if (Boolean.TRUE.equals(period.getClosed())) {
            log.debug("Period {} is closed; skipping snapshot rebuild", period.getId());
            warnings = List.of();
        } else {
            warnings = buildAndPersistSnapshots(List.of(period));
            log.info("Snapshot rebuilt for period {}: {} warning(s)",
                    period.getId(), warnings.size());
        }

        // Fetch only the rows that belong to the requested dimension.
        List<PayrollPeriodEarnerRanking> inScopeRankings =
                payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                        period.getId(),
                        request.countryId(), request.departmentId(),
                        request.roleId(), request.employmentType());
        List<PayrollPeriodEarnerPercentile> inScopePercentiles =
                payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                        period.getId(),
                        request.countryId(), request.departmentId(),
                        request.roleId(), request.employmentType());

        log.debug("Fetched {} ranking row(s) and {} percentile row(s) for period {} (group={})",
                inScopeRankings.size(), inScopePercentiles.size(),
                period.getId(), request.groupKey());

        PositionContextDto positionContext = buildPositionContext(inScopePercentiles);
        int totalEmployees = positionContext == null ? 0 : positionContext.employeeCount();

        List<EarnerRankingDto> tops = inScopeRankings.stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP)
                .sorted(Comparator.comparing(PayrollPeriodEarnerRanking::getRank))
                .map(r -> toRankingDto(r, positionContext, period, null))
                .toList();
        List<EarnerRankingDto> bottoms = inScopeRankings.stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM)
                .sorted(Comparator.comparing(PayrollPeriodEarnerRanking::getRank))
                .map(r -> toRankingDto(r, positionContext, period, totalEmployees))
                .toList();

        BigDecimal totalReferenceAmount =
                aggregateRepository.sumReferenceTotalByPeriodId(period.getId());
        BigDecimal totalDimensionReferenceAmount =
                aggregateRepository.sumReferenceTotalByPeriodAndDimension(
                        period.getId(),
                        request.countryId(), request.departmentId(),
                        request.roleId(), request.employmentType());

        EarningRankingReportResponse response = EarningRankingReportResponse.builder()
                .kind(ReportKind.RANKING)
                .period(toPeriodResponse(period))
                .referenceCurrency(referenceCurrency())
                .groupKey(request.groupKey())
                .tops(tops)
                .bottoms(bottoms)
                .positionContext(positionContext)
                .totalReferenceAmount(totalReferenceAmount)
                .totalDimensionReferenceAmount(totalDimensionReferenceAmount)
                .totalMonthlyPeriod(PayrollPeriodFrequency.MONTHLY.equals(period.getFrequency()) ? 1 : 0)
                .totalBiWeeklyPeriod(PayrollPeriodFrequency.BI_WEEKLY.equals(period.getFrequency()) ? 1 : 0)
                .build();

        log.info("Ranking report generated: periodId={}, groupKey={}, tops={}, bottoms={}, "
                        + "percentilePoints={}, totalReferenceAmount={}, totalDimensionReferenceAmount={}",
                period.getId(), request.groupKey(),
                tops.size(), bottoms.size(),
                positionContext == null ? 0 : 7,
                totalReferenceAmount, totalDimensionReferenceAmount);

        return response;
    }
    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    private void validate(EarningRankingReportRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        if (request.periodId() == null) {
            errors.add(new ValidationError("periodId", "Missing",
                    "periodId is required", Map.of()));
        }
        validateGroupKey(errors, request.groupKey(),
                request.countryId(), request.departmentId(),
                request.roleId(), request.employmentType());
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }

    /** Appends validation errors when the group key and dimension fields disagree. */
    private void validateGroupKey(List<ValidationError> errors, GroupKey key,
                                  UUID countryId, UUID departmentId,
                                  UUID roleId, EmploymentType employmentType) {
        if (key == null) {
            if (countryId != null || departmentId != null
                    || roleId != null || employmentType != null) {
                errors.add(new ValidationError("groupKey", "DimensionMismatch",
                        "when groupKey is null, no dimension id may be provided",
                        Map.of()));
            }
            return;
        }
        switch (key) {
            case COUNTRY -> {
                if (countryId == null) {
                    errors.add(missing("countryId", "COUNTRY"));
                }
                if (departmentId != null || roleId != null || employmentType != null) {
                    errors.add(extra("COUNTRY", "departmentId", "roleId", "employmentType"));
                }
            }
            case DEPARTMENT -> {
                if (departmentId == null) {
                    errors.add(missing("departmentId", "DEPARTMENT"));
                }
                if (countryId != null || roleId != null || employmentType != null) {
                    errors.add(extra("DEPARTMENT", "countryId", "roleId", "employmentType"));
                }
            }
            case ROLE -> {
                if (roleId == null) {
                    errors.add(missing("roleId", "ROLE"));
                }
                if (countryId != null || departmentId != null || employmentType != null) {
                    errors.add(extra("ROLE", "countryId", "departmentId", "employmentType"));
                }
            }
            case EMPLOYMENT_TYPE -> {
                if (employmentType == null) {
                    errors.add(missing("employmentType", "EMPLOYMENT_TYPE"));
                }
                if (countryId != null || departmentId != null || roleId != null) {
                    errors.add(extra("EMPLOYMENT_TYPE", "countryId", "departmentId", "roleId"));
                }
            }
            case CURRENCY, ORIGIN_COUNTRY -> errors.add(new ValidationError(
                    "groupKey", "Unsupported",
                    "groupKey " + key + " is not supported", Map.of()));
        }
    }

    private ValidationError missing(String field, String key) {
        return new ValidationError(field, "Missing",
                field + " is required when groupKey = " + key, Map.of());
    }

    private ValidationError extra(String key, String... fields) {
        return new ValidationError("groupKey", "ExtraFields",
                "when groupKey = " + key + ", the following fields must be null: "
                        + String.join(", ", fields), Map.of());
    }


    /**
     * Tests whether a row's dimension columns belong to the requested group.
     * A single-dimension row has exactly one populated dimension column;
     * a Global row has none.
     */
    private boolean matchesDimension(UUID country, UUID dept, UUID role,
                                     EmploymentType type, GroupKey key,
                                     UUID reqCountry, UUID reqDept, UUID reqRole,
                                     EmploymentType reqType) {
        if (key == null) {
            return country == null && dept == null && role == null && type == null;
        }
        return switch (key) {
            case COUNTRY -> country != null && country.equals(reqCountry)
                    && dept == null && role == null && type == null;
            case DEPARTMENT -> dept != null && dept.equals(reqDept)
                    && country == null && role == null && type == null;
            case ROLE -> role != null && role.equals(reqRole)
                    && country == null && dept == null && type == null;
            case EMPLOYMENT_TYPE -> type != null && type.equals(reqType)
                    && country == null && dept == null && role == null;
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported: " + key);
        };
    }

    // ------------------------------------------------------------------
    // Percentile distribution
    // ------------------------------------------------------------------

    /** Builds the 7-point position context; {@code null} when no percentiles are present. */
    private PositionContextDto buildPositionContext(
            List<PayrollPeriodEarnerPercentile> percentiles) {
        if (percentiles.isEmpty()) {
            return null;
        }
        Map<Integer, PayrollPeriodEarnerPercentile> byPoint = new TreeMap<>();
        for (PayrollPeriodEarnerPercentile p : percentiles) {
            byPoint.putIfAbsent(p.getPercentile(), p);
        }
        PayrollPeriodEarnerPercentile p0 = byPoint.get(0);
        if (p0 == null) {
            return null;
        }
        return new PositionContextDto(
                p0.getEmployeeCount(),
                amountOf(byPoint.get(0)),
                amountOf(byPoint.get(10)),
                amountOf(byPoint.get(25)),
                amountOf(byPoint.get(50)),
                amountOf(byPoint.get(75)),
                amountOf(byPoint.get(90)),
                amountOf(byPoint.get(100)));
    }

    private BigDecimal amountOf(PayrollPeriodEarnerPercentile p) {
        return p == null || p.getReferenceAmount() == null
                ? null
                : p.getReferenceAmount().getAmount();
    }

    /**
     * Approximates the employee's percentile badge (in basis points) by
     * linear interpolation between the two bracketing percentile points.
     * Returns {@code null} when the distribution is missing or incomplete.
     */
    private Integer approximatePercentile(BigDecimal value, PositionContextDto ctx) {
        if (value == null || ctx == null) {
            return null;
        }
        BigDecimal[] amounts = {
                ctx.min(), ctx.p10(), ctx.p25(), ctx.p50(),
                ctx.p75(), ctx.p90(), ctx.max()
        };
        for (BigDecimal amount : amounts) {
            if (amount == null) {
                return null;
            }
        }
        if (value.compareTo(amounts[0]) <= 0) {
            return 0;
        }
        if (value.compareTo(amounts[amounts.length - 1]) >= 0) {
            return BASIS_POINTS;
        }
        for (int i = 1; i < amounts.length; i++) {
            BigDecimal lo = amounts[i - 1];
            BigDecimal hi = amounts[i];
            if (value.compareTo(lo) >= 0 && value.compareTo(hi) < 0) {
                if (hi.compareTo(lo) == 0) {
                    return PERCENTILE_LABELS[i] * PERCENTILE_STEP;
                }
                BigDecimal fraction = value.subtract(lo)
                        .divide(hi.subtract(lo), 10, RoundingMode.HALF_UP);
                BigDecimal label = BigDecimal.valueOf(PERCENTILE_LABELS[i - 1]).add(
                        fraction.multiply(BigDecimal.valueOf(
                                PERCENTILE_LABELS[i] - PERCENTILE_LABELS[i - 1])));
                return label.multiply(BigDecimal.valueOf(PERCENTILE_STEP))
                        .setScale(0, RoundingMode.HALF_UP)
                        .intValueExact();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // DTO mapping
    // ------------------------------------------------------------------

    /**
     * @param totalEmployees for BOTTOM rows, the population size; used to
     *                       invert the stored rank into a position N - rank + 1.
     *                       Pass {@code null} to keep the stored rank.
     */
    private EarnerRankingDto toRankingDto(PayrollPeriodEarnerRanking r,
                                          PositionContextDto distribution,
                                          PayrollPeriod period,
                                          Integer totalEmployees) {
        BigDecimal monthlyRate = r.getMonthlyRateAmount() == null
                ? null
                : r.getMonthlyRateAmount().getAmount();

        Integer displayRank = r.getRank();
        if (totalEmployees != null && totalEmployees > 0) {
            displayRank = totalEmployees - r.getRank() + 1;
        }
        // Get the assignment of the employee at the period of the ranking
        var assignment = periodicAssignmentRepository.findByEmployeeIdAndDateRange(
                r.getEmployee().getId(), period.getStartDate(), period.getEndDate());

        RefDto role          = assignment.map(a -> refDto(a.getRole())).orElseGet(() -> refDto(r.getRole()));
        RefDto department    = assignment.map(a -> refDto(a.getDepartment())).orElseGet(() -> refDto(r.getDepartment()));
        RefDto country       = assignment.map(a -> refDto(a.getCountry())).orElseGet(() -> refDto(r.getCountry()));
        EmploymentType type  = assignment.map(EmployeePeriodicAssignment::getEmploymentType).orElseGet(r::getEmploymentType);
        PayrollPeriodFrequency frequency = assignment.map(EmployeePeriodicAssignment::getPayrollFrequency).orElseGet(period::getFrequency);

        return EarnerRankingDto.builder()
                .monthlyReferenceAmount(monthlyRate)
                .frequency(frequency)
                .percentile(approximatePercentile(monthlyRate, distribution))
                .rank(displayRank)
                .employee(employeeRef(r))
                .role(role)
                .department(department)
                .country(country)
                .employmentType(type)
                .period(toPeriodResponse(period))
                .build();
    }

    private EmployeeRefDto employeeRef(PayrollPeriodEarnerRanking r) {
        return new EmployeeRefDto(
                r.getEmployee().getId(),
                r.getEmployee().getMatricule(),
                r.getEmployee().getName());
    }

    private RefDto refDto(Country c) {
        return c == null ? null : new RefDto(c.getId(), c.getIsoCode(), c.getName());
    }

    private RefDto refDto(Department d) {
        return d == null ? null : new RefDto(d.getId(), d.getCode(), d.getName());
    }

    private RefDto refDto(Role r) {
        return r == null ? null : new RefDto(r.getId(), r.getCode(), r.getName());
    }

    private PayrollPeriodResponse toPeriodResponse(PayrollPeriod p) {
        return PayrollPeriodResponse.builder()
                .id(p.getId().toString())
                .name(p.getName())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .frequency(p.getFrequency())
                .closed(p.getClosed())
                .build();
    }
    /** Compact description of the requested dimension, for logging. */
    private String describeDimension(EarningRankingReportRequest request) {
        if(request.groupKey() == null) return "GLOBAL";
        return switch (request.groupKey()) {
            case COUNTRY -> "COUNTRY:" + request.countryId();
            case DEPARTMENT -> "DEPARTMENT:" + request.departmentId();
            case ROLE -> "ROLE:" + request.roleId();
            case EMPLOYMENT_TYPE -> "EMPLOYMENT_TYPE:" + request.employmentType();
            case CURRENCY, ORIGIN_COUNTRY -> "UNSUPPORTED:" + request.groupKey();
        };
    }
}