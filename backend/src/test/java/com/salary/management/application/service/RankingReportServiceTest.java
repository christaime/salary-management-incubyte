package com.salary.management.application.service;

import com.salary.management.application.dto.ranking.EarnerRankingDto;
import com.salary.management.application.dto.ranking.EarningRankingReportRequest;
import com.salary.management.application.dto.ranking.EarningRankingReportResponse;
import com.salary.management.application.dto.ranking.PositionContextDto;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodEarnerPercentile;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.values.Amount;
import com.salary.management.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RankingReportService}.
 *
 * <p>Two invariants are pinned here:</p>
 * <ul>
 *   <li>The <b>position context</b> ({@link PositionContextDto}) is
 *       <b>annualized</b> — the percentile bands produced by
 *       {@code DynamicRankingBuilder.computePercentiles} are annualized
 *       amounts (see {@code PayrollPeriodEarnerPercentileEntity.referenceAmount}).</li>
 *   <li>The <b>ranking row's monthly rate</b> is monthly. Before comparing it
 *       against the annualized context, {@code RankingReportService} multiplies
 *       it by {@value #MONTHS_PER_YEAR}.</li>
 * </ul>
 *
 * <p>Every percentile test below builds an <b>annualized</b> context and passes
 * a <b>monthly</b> ranking rate, so the interpolation math is visible in the
 * test. The two regression tests
 * ({@link #rankingMonthlyRateIsAnnualizedBeforePercentileComparison()} and
 * {@link #regression_topOneMapsToHundredthPercentile()}) fail with {@code 0 bp}
 * if the {@code × 12} conversion is ever removed from
 * {@code RankingReportService.toRankingDto}.</p>
 */
@ExtendWith(MockitoExtension.class)
class RankingReportServiceTest {

    private static final String REFERENCE_CURRENCY = "USD";

    /**
     * Kept in sync with {@code RankingReportService.MONTHS_PER_YEAR}. Declared
     * here so the test explicitly documents the conversion the service performs.
     */
    private static final int MONTHS_PER_YEAR = 12;

    @Mock
    private PayrollPeriodRepository payrollPeriodRepository;

    @Mock
    private PayrollPeriodAggregateRepository payrollPeriodAggregateRepository;

    @Mock
    private PayrollPeriodEarnerRankingRepository payrollPeriodEarnerRankingRepository;

    @Mock
    private EmployeePeriodicAssignmentRepository periodicAssignmentRepository;

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @Mock
    private PayrollPeriodSnapshotRepository payrollPeriodSnapshotRepository;

    private RankingReportService service;

    private UUID PERIOD_ID;
    private UUID FR;
    private UUID DE;
    private UUID ENGINEERING;
    private UUID ENGINEER;
    private UUID EMPLOYEE_1;
    private UUID EMPLOYEE_2;

    @BeforeEach
    void setup() {
        PERIOD_ID = UUID.randomUUID();
        FR = UUID.randomUUID();
        DE = UUID.randomUUID();
        ENGINEERING = UUID.randomUUID();
        ENGINEER = UUID.randomUUID();
        EMPLOYEE_1 = UUID.randomUUID();
        EMPLOYEE_2 = UUID.randomUUID();

        service = new RankingReportService(
                payrollPeriodRepository,
                payrollPeriodEarnerRankingRepository,
                payrollPeriodAggregateRepository,
                currencyRateRepository,
                periodicAssignmentRepository,
                payrollPeriodSnapshotRepository);

        // Default: no assignment matches the period range, so DTO dimension
        // fields fall back to the ranking row's captured values.
        lenient().when(periodicAssignmentRepository.findByEmployeeIdAndDateRange(
                any(), any(), any())).thenReturn(Optional.empty());
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("null periodId throws DomainValidationException with Missing code")
    void nullPeriodIdThrows() {
        EarningRankingReportRequest request = request(null, GroupKey.COUNTRY, FR);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Missing", ex.getErrors().get(0).code());
        assertEquals("periodId", ex.getErrors().get(0).field());
    }

    @Test
    @DisplayName("null groupKey with a dimension id throws DimensionMismatch")
    void nullKeyWithDimensionThrows() {
        EarningRankingReportRequest request = request(PERIOD_ID, null, FR);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("DimensionMismatch", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("COUNTRY key without countryId throws Missing")
    void countryKeyWithoutCountryIdThrows() {
        EarningRankingReportRequest request = request(PERIOD_ID, GroupKey.COUNTRY, null);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Missing", ex.getErrors().get(0).code());
        assertEquals("countryId", ex.getErrors().get(0).field());
    }

    @Test
    @DisplayName("COUNTRY key with extra department id throws ExtraFields")
    void countryKeyWithExtraDepartmentThrows() {
        EarningRankingReportRequest request = new EarningRankingReportRequest(
                PERIOD_ID, GroupKey.COUNTRY, FR, ENGINEERING, null, null);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("ExtraFields", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("DEPARTMENT key without departmentId throws Missing")
    void departmentKeyWithoutDepartmentIdThrows() {
        EarningRankingReportRequest request = request(PERIOD_ID, GroupKey.DEPARTMENT, null);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Missing", ex.getErrors().get(0).code());
        assertEquals("departmentId", ex.getErrors().get(0).field());
    }

    @Test
    @DisplayName("ROLE key without roleId throws Missing")
    void roleKeyWithoutRoleIdThrows() {
        EarningRankingReportRequest request = request(PERIOD_ID, GroupKey.ROLE, null);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Missing", ex.getErrors().get(0).code());
        assertEquals("roleId", ex.getErrors().get(0).field());
    }

    @Test
    @DisplayName("EMPLOYMENT_TYPE key without employmentType throws Missing")
    void employmentTypeKeyWithoutTypeThrows() {
        EarningRankingReportRequest request = new EarningRankingReportRequest(
                PERIOD_ID, GroupKey.EMPLOYMENT_TYPE, null, null, null, null);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Missing", ex.getErrors().get(0).code());
        assertEquals("employmentType", ex.getErrors().get(0).field());
    }

    @Test
    @DisplayName("CURRENCY key is rejected as Unsupported")
    void currencyKeyThrows() {
        EarningRankingReportRequest request = request(PERIOD_ID, GroupKey.CURRENCY, FR);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Unsupported", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("multiple validation errors accumulate")
    void validationErrorsAccumulate() {
        EarningRankingReportRequest request = new EarningRankingReportRequest(
                null, GroupKey.COUNTRY, null, null, null, null);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals(2, ex.getErrors().size());
    }

    // ------------------------------------------------------------------
    // Period resolution + snapshot
    // ------------------------------------------------------------------

    @Test
    @DisplayName("missing period throws DomainValidationException with NotFound")
    void periodNotFoundThrows() {
        when(payrollPeriodRepository.findById(PERIOD_ID)).thenReturn(Optional.empty());

        DomainValidationException ex = assertThrows(
                DomainValidationException.class,
                () -> service.generate(request(PERIOD_ID, null, null)));
        assertEquals("NotFound", ex.getErrors().get(0).code());
    }

    // ------------------------------------------------------------------
    // Empty result
    // ------------------------------------------------------------------

    @Test
    @DisplayName("empty period yields empty tops, bottoms, position context, and zero totals")
    void emptyPeriodYieldsEmptyReport() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));
        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response =
                service.generate(request(PERIOD_ID, null, null));

        assertEquals(ReportKind.RANKING, response.kind());
        assertNotNull(response.period());
        assertEquals(REFERENCE_CURRENCY, response.referenceCurrency());
        assertNull(response.groupKey());
        assertTrue(response.tops().isEmpty());
        assertTrue(response.bottoms().isEmpty());
        assertNull(response.positionContext());
        assertEquals(0, response.totalReferenceAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, response.totalDimensionReferenceAmount().compareTo(BigDecimal.ZERO));
        assertEquals(1, response.totalMonthlyPeriod());
        assertEquals(0, response.totalBiWeeklyPeriod());
    }

    // ------------------------------------------------------------------
    // Dimension filtering
    // ------------------------------------------------------------------

    @Test
    @DisplayName("COUNTRY key only includes rows with matching country")
    void countryKeyFiltersRows() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking frTop = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(2000.00), "USD"), FR, null, null, null);
        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), eq(FR), any(), any(), any())).thenReturn(List.of(frTop));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), eq(FR), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), eq(FR), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, GroupKey.COUNTRY, FR));

        assertEquals(1, response.tops().size());
        assertEquals(FR, response.tops().get(0).country().id());
    }

    @Test
    @DisplayName("global key only includes rows with all dimensions null")
    void globalKeyFiltersRows() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking globalTop = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(2000.00), "USD"), null, null, null, null);
        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(List.of(globalTop));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), eq(null), eq(null), eq(null), eq(null)))
                .thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(1, response.tops().size());
        assertNull(response.tops().get(0).country());
    }

    // ------------------------------------------------------------------
    // Top/Bottom split
    // ------------------------------------------------------------------

    @Test
    @DisplayName("tops and bottoms are split by direction")
    void topsAndBottomsAreSplit() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking top1 = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(2000.00), "USD"), null, null, null, null);
        PayrollPeriodEarnerRanking top2 = ranking(EarnerRankingDirection.TOP, 2,
                Amount.of(BigDecimal.valueOf(1500.00), "USD"), null, null, null, null);
        PayrollPeriodEarnerRanking bottom1 = ranking(EarnerRankingDirection.BOTTOM, 1,
                Amount.of(BigDecimal.valueOf(500.00), "USD"), null, null, null, null);
        PayrollPeriodEarnerRanking bottom2 = ranking(EarnerRankingDirection.BOTTOM, 2,
                Amount.of(BigDecimal.valueOf(800.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(List.of(top1, top2, bottom1, bottom2));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(2, response.tops().size());
        assertEquals(2, response.bottoms().size());
        assertEquals(1, response.tops().get(0).rank());
        assertEquals(2, response.tops().get(1).rank());
    }

    // ------------------------------------------------------------------
    // BOTTOM rank inversion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("BOTTOM ranks are inverted using the population size from percentiles")
    void bottomRanksAreInverted() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking bottom1 = ranking(EarnerRankingDirection.BOTTOM, 1,
                Amount.of(BigDecimal.valueOf(500.00), "USD"), null, null, null, null);
        PayrollPeriodEarnerRanking bottom2 = ranking(EarnerRankingDirection.BOTTOM, 2,
                Amount.of(BigDecimal.valueOf(800.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(List.of(bottom1, bottom2));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(percentilesWithN(10));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(10, response.bottoms().get(0).rank());
        assertEquals(9, response.bottoms().get(1).rank());
    }

    @Test
    @DisplayName("BOTTOM ranks keep the stored rank when no percentiles are present")
    void bottomRanksKeepStoredRankWithoutPercentiles() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking bottom1 = ranking(EarnerRankingDirection.BOTTOM, 1,
                Amount.of(BigDecimal.valueOf(500.00), "USD"), null, null, null, null);
        PayrollPeriodEarnerRanking bottom2 = ranking(EarnerRankingDirection.BOTTOM, 2,
                Amount.of(BigDecimal.valueOf(800.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(List.of(bottom1, bottom2));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(1, response.bottoms().get(0).rank());
        assertEquals(2, response.bottoms().get(1).rank());
    }

    @Test
    @DisplayName("percentile badge is interpolated between two annualized points")
    void percentileBadgeInterpolates() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        // Ranking rate is MONTHLY (450). After ×12 → 5400, between p25 (3600)
        // and p50 (6000) of the annualized context.
        // Interpolation: 25 + (5400-3600)/(6000-3600) * (50-25)
        //             = 25 + 0.75 * 25 = 43.75 → 4375 bp.
        PayrollPeriodEarnerRanking top1 = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(450.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of(top1));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(annualizedPercentiles(
                        "1200.00", "2400.00", "3600.00", "6000.00",
                        "8400.00", "9600.00", "10800.00", 20));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(4375, response.tops().get(0).percentile());

        // The DTO field stays monthly; the ×12 was only for the badge computation.
        assertEquals(0, response.tops().get(0).monthlyReferenceAmount()
                        .compareTo(new BigDecimal("450.00")),
                "monthlyReferenceAmount must stay monthly, not annualized");
    }

    @Test
    @DisplayName("percentile badge clamps to 0 below P0")
    void percentileBadgeClampsBelowP0() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        // Ranking rate is MONTHLY (50). After ×12 → 600, below the annualized
        // min (1200), so the badge clamps to 0.
        PayrollPeriodEarnerRanking bottom1 = ranking(EarnerRankingDirection.BOTTOM, 1,
                Amount.of(BigDecimal.valueOf(50.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of(bottom1));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(annualizedPercentiles(
                        "1200.00", "2400.00", "3600.00", "6000.00",
                        "8400.00", "9600.00", "10800.00", 20));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(0, response.bottoms().get(0).percentile());
    }

    @Test
    @DisplayName("percentile badge clamps to 10000 above P100")
    void percentileBadgeClampsAboveP100() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        // Ranking rate is MONTHLY (1000). After ×12 → 12000, above the
        // annualized max (10800), so the badge clamps to 10000.
        PayrollPeriodEarnerRanking top1 = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(1000.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of(top1));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(annualizedPercentiles(
                        "1200.00", "2400.00", "3600.00", "6000.00",
                        "8400.00", "9600.00", "10800.00", 20));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(10000, response.tops().get(0).percentile());
    }

    @Test
    @DisplayName("percentile badge is null when the distribution is missing")
    void percentileBadgeNullWithoutDistribution() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking top1 = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(2000.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of(top1));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertNull(response.tops().get(0).percentile());
    }

    /**
     * Load-bearing test for the {@code × 12} conversion.
     *
     * <p>Monthly rate = 250. If the conversion is applied, 250 × 12 = 3000,
     * which lands inside the annualized distribution (between p10 = 2400 and
     * p25 = 3600), producing a badge of {@code 1750 bp}. Without the
     * conversion, the monthly 250 is below the annualized min (1200), and
     * the badge would be {@code 0 bp}.</p>
     *
     * <p>If this test fails with {@code 0}, someone removed the {@code × 12}
     * from {@code RankingReportService.toRankingDto}.</p>
     */
    @Test
    @DisplayName("ranking monthly rate is annualized (×12) before comparing to the annualized context")
    void rankingMonthlyRateIsAnnualizedBeforePercentileComparison() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking top1 = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(250.00), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of(top1));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(annualizedPercentiles(
                        "1200.00", "2400.00", "3600.00", "6000.00",
                        "8400.00", "9600.00", "10800.00", 20));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        Integer badge = response.tops().get(0).percentile();

        assertNotNull(badge);
        // Monthly 250 → annualized 3000 → falls in [p10=2400, p25=3600).
        // label = 10 + (3000 - 2400) / (3600 - 2400) * (25 - 10) = 17.5
        // badge = 17.5 * 100 = 1750 bp
        assertEquals(1750, badge,
                "Expected 1750 bp (monthly 250 annualized to 3000 lands between "
                        + "p10=2400 and p25=3600). If you got 0, the ×12 conversion in "
                        + "RankingReportService.toRankingDto is missing.");
    }

    /**
     * End-to-end regression for the 2026-11-01 bug report: with a real
     * annualized context and a real monthly rate (25106.63 monthly vs.
     * min=27648.64, max=301279.59 annualized), the top-1 employee landed
     * on {@code 0 bp} because the monthly rate was compared directly against
     * annualized values.
     *
     * <p>After the fix, {@code 25106.63 × 12 = 301279.56}, which is just
     * below the annualized max (301279.59), so the badge clamps to
     * {@code 10000 bp}.</p>
     */
    @Test
    @DisplayName("2026-11-01 regression: top-1 with monthly rate just below annualized max")
    void regression_topOneMapsToHundredthPercentile() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking top1 = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(new BigDecimal("25106.63"), "USD"), null, null, null, null);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of(top1));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(annualizedPercentiles(
                        "27648.64", "71657.24", "96296.85", "129878.84",
                        "165622.31", "208023.03", "301279.59", 10000));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(10000, response.tops().get(0).percentile(),
                "Monthly 25106.63 × 12 = 301279.56, just below the annualized max "
                        + "301279.59 → clamps to 10000 bp. Without ×12, "
                        + "25106.63 < 27648.64 (annualized min) → 0 bp.");
    }

    // ------------------------------------------------------------------
    // Position context
    // ------------------------------------------------------------------

    @Test
    @DisplayName("position context is built from percentile rows")
    void positionContextIsBuilt() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));
        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any()))
                .thenReturn(annualizedPercentiles(
                        "100.00", "200.00", "300.00", "500.00",
                        "700.00", "800.00", "900.00", 20));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        PositionContextDto ctx = response.positionContext();
        assertNotNull(ctx);
        assertEquals(20, ctx.employeeCount());
        assertEquals(0, ctx.min().compareTo(new BigDecimal("100.00")));
        assertEquals(0, ctx.p10().compareTo(new BigDecimal("200.00")));
        assertEquals(0, ctx.p25().compareTo(new BigDecimal("300.00")));
        assertEquals(0, ctx.p50().compareTo(new BigDecimal("500.00")));
        assertEquals(0, ctx.p75().compareTo(new BigDecimal("700.00")));
        assertEquals(0, ctx.p90().compareTo(new BigDecimal("800.00")));
        assertEquals(0, ctx.max().compareTo(new BigDecimal("900.00")));
    }

    @Test
    @DisplayName("position context is null when there are no percentile rows")
    void positionContextNullWhenEmpty() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));
        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertNull(response.positionContext());
    }

    // ------------------------------------------------------------------
    // Totals
    // ------------------------------------------------------------------

    @Test
    @DisplayName("global and dimension totals are fetched from the aggregates")
    void totalsAreFetched() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));
        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(new BigDecimal("5000.00"));
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), eq(FR), any(), any(), any()))
                .thenReturn(new BigDecimal("2000.00"));

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, GroupKey.COUNTRY, FR));

        assertEquals(0, response.totalReferenceAmount()
                .compareTo(new BigDecimal("5000.00")));
        assertEquals(0, response.totalDimensionReferenceAmount()
                .compareTo(new BigDecimal("2000.00")));
    }

    // ------------------------------------------------------------------
    // Period counts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("monthly period sets totalMonthlyPeriod = 1")
    void monthlyPeriodCount() {
        stubEmptyPeriod(PayrollPeriodFrequency.MONTHLY);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(1, response.totalMonthlyPeriod());
        assertEquals(0, response.totalBiWeeklyPeriod());
    }

    @Test
    @DisplayName("bi-weekly period sets totalBiWeeklyPeriod = 1")
    void biWeeklyPeriodCount() {
        stubEmptyPeriod(PayrollPeriodFrequency.BI_WEEKLY);

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        assertEquals(0, response.totalMonthlyPeriod());
        assertEquals(1, response.totalBiWeeklyPeriod());
    }

    // ------------------------------------------------------------------
    // Assignment fallback
    // ------------------------------------------------------------------

    @Test
    @DisplayName("dimension refs come from the assignment when one matches the period range")
    void dimensionRefsFromAssignment() {
        stubPeriod(period(PayrollPeriodFrequency.MONTHLY, true));

        PayrollPeriodEarnerRanking top = ranking(EarnerRankingDirection.TOP, 1,
                Amount.of(BigDecimal.valueOf(2000.00), "USD"), FR, null, null,
                EmploymentType.FULL_TIME);

        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of(top));
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);

        EmployeePeriodicAssignment assignment = EmployeePeriodicAssignment.builder()
                .id(UUID.randomUUID())
                .employeeId(EMPLOYEE_1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(null)
                .country(Country.builder()
                        .id(DE).isoCode("DE").name("Germany").currencyCode("EUR").build())
                .department(Department.builder()
                        .id(ENGINEERING).code("ENG").name("Engineering").build())
                .role(Role.builder()
                        .id(ENGINEER).code("SWE").name("Engineer").build())
                .employmentType(EmploymentType.PART_TIME)
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY)
                .inEffect(true)
                .build();
        when(periodicAssignmentRepository.findByEmployeeIdAndDateRange(
                eq(EMPLOYEE_1), any(), any())).thenReturn(Optional.of(assignment));

        EarningRankingReportResponse response = service.generate(
                request(PERIOD_ID, null, null));

        EarnerRankingDto dto = response.tops().get(0);
        assertEquals(DE, dto.country().id());
        assertEquals(ENGINEERING, dto.department().id());
        assertEquals(ENGINEER, dto.role().id());
        assertEquals(EmploymentType.PART_TIME, dto.employmentType());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private EarningRankingReportRequest request(UUID periodId, GroupKey key, UUID countryId) {
        return new EarningRankingReportRequest(periodId, key, countryId, null, null, null);
    }

    private PayrollPeriod period(PayrollPeriodFrequency freq, boolean closed) {
        return PayrollPeriod.builder()
                .id(PERIOD_ID)
                .name("January 2026")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 31))
                .frequency(freq)
                .closed(closed)
                .build();
    }

    private void stubPeriod(PayrollPeriod period) {
        when(payrollPeriodRepository.findById(PERIOD_ID)).thenReturn(Optional.of(period));
        when(currencyRateRepository.getReferenceCurrencyCode()).thenReturn(REFERENCE_CURRENCY);
    }

    private void stubEmptyPeriod(PayrollPeriodFrequency freq) {
        stubPeriod(period(freq, true));
        when(payrollPeriodEarnerRankingRepository.findByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodEarnerRankingRepository.findPercentileByPayrollPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(List.of());
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodId(PERIOD_ID))
                .thenReturn(BigDecimal.ZERO);
        when(payrollPeriodAggregateRepository.sumReferenceTotalByPeriodAndDimension(
                eq(PERIOD_ID), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
    }

    /**
     * Builds a ranking row whose {@code monthlyRateAmount} is a <b>monthly</b>
     * value. The {@code RankingReportService} multiplies it by 12 before
     * comparing it against the annualized {@link PositionContextDto}.
     */
    private PayrollPeriodEarnerRanking ranking(EarnerRankingDirection direction, int rank,
                                               Amount monthlyRate,
                                               UUID country, UUID department,
                                               UUID role, EmploymentType type) {
        return PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(period(PayrollPeriodFrequency.MONTHLY, true))
                .status(PayrollSummaryStatus.VALIDATED)
                .country(country == null ? null : Country.builder()
                        .id(country).isoCode("XX").name("Placeholder").currencyCode("XXX").build())
                .department(department == null ? null : Department.builder()
                        .id(department).code("ENG").name("Engineering").build())
                .role(role == null ? null : Role.builder()
                        .id(role).code("SWE").name("Engineer").build())
                .employmentType(type)
                .direction(direction)
                .rank(rank)
                .employee(Employee.builder()
                        .id(EMPLOYEE_1).matricule("EMP-001").name("Test Employee")
                        .email("test@example.com").build())
                .originalAmount(monthlyRate)
                .referenceAmount(monthlyRate)
                .daysCovered(31)
                .monthlyRateAmount(monthlyRate)
                .build();
    }

    /**
     * Same 7-point distribution as
     * {@link #annualizedPercentiles(String, String, String, String, String, String, String, int)},
     * but with a population count of 10 — the minimum required to compute percentiles.
     */
    private List<PayrollPeriodEarnerPercentile> percentilesWithN(int n) {
        return annualizedPercentiles(
                "1000.00", "2000.00", "3000.00", "5000.00",
                "7000.00", "8000.00", "10000.00", n);
    }

    /**
     * Builds an <b>annualized</b> percentile distribution — the amounts stored
     * in {@code PayrollPeriodEarnerPercentileEntity.referenceAmount} are annualized
     * (monthly × 12), because that is what the report shows and what
     * {@code DynamicRankingBuilder.computePercentiles} produces.
     *
     * <p>Tests that exercise the badge build the context from annualized values
     * and pass a <b>monthly</b> ranking rate, matching production behavior.</p>
     */
    private List<PayrollPeriodEarnerPercentile> annualizedPercentiles(
            String p0, String p10, String p25, String p50,
            String p75, String p90, String p100, int n) {
        PayrollPeriod period = period(PayrollPeriodFrequency.MONTHLY, true);
        List<PayrollPeriodEarnerPercentile> out = new ArrayList<>();
        int[] points = {0, 10, 25, 50, 75, 90, 100};
        String[] amounts = {p0, p10, p25, p50, p75, p90, p100};
        for (int i = 0; i < points.length; i++) {
            out.add(PayrollPeriodEarnerPercentile.builder()
                    .payrollPeriod(period)
                    .status(PayrollSummaryStatus.VALIDATED)
                    .percentile(points[i])
                    .referenceAmount(Amount.of(new BigDecimal(amounts[i]), "USD"))
                    .employeeCount(n)
                    .build());
        }
        return out;
    }
}