package com.salary.management.application.service;

import com.salary.management.application.dto.AggregateLineDto;
import com.salary.management.application.dto.AggregateReportRequest;
import com.salary.management.application.dto.AggregateReportResponse;
import com.salary.management.application.dto.DateRange;
import com.salary.management.application.dto.RefDto;
import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.application.snapshot.SnapshotBuilder;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.enums.ReportWarningCode;
import com.salary.management.domain.model.values.RankingAndPercentileSnapshot;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link AggregateReportService}. */
@ExtendWith(MockitoExtension.class)
class AggregateReportServiceTest {

    private static final String REFERENCE_CURRENCY = "USD";

    @Mock
    private PayrollPeriodRepository payrollPeriodRepository;

    @Mock
    private PayrollPeriodAggregateRepository payrollPeriodAggregateRepository;

    @Mock
    private PayrollPeriodEarnerRankingRepository payrollPeriodEarnerRankingRepository;

    @Mock
    private ReportDataRepository reportDataRepository;

    @Mock
    private SnapshotBuilder snapshotBuilder;

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @InjectMocks
    private AggregateReportService service;

    private UUID FR_UUID;

    @BeforeEach
    void setup() {
        FR_UUID = UUID.randomUUID();
        when(currencyRateRepository.getReferenceCurrencyCode()).thenReturn(REFERENCE_CURRENCY);
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("reversed date range throws DomainValidationException")
    void reversedDateRangeThrows() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1),
                List.of(), false, false);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("DateOrder", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("future endDate throws DomainValidationException")
    void futureEndDateThrows() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.now().plusDays(1),
                List.of(), false, false);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("FutureDate", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("duplicate group keys throw DomainValidationException")
    void duplicateGroupKeysThrow() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY, GroupKey.COUNTRY), false, false);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Duplicate", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("ORIGIN_COUNTRY in groupKeys throws DomainValidationException")
    void originCountryGroupKeyThrows() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.ORIGIN_COUNTRY), false, false);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("Unsupported", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("includeStatistics with more than one group key throws")
    void statisticsWithMultipleGroupKeysThrows() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY, GroupKey.DEPARTMENT), false, true);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals("StatisticsSingleDimension", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("multiple validation errors accumulate before throwing")
    void validationErrorsAccumulate() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1),
                List.of(GroupKey.COUNTRY, GroupKey.COUNTRY), false, false);

        DomainValidationException ex = assertThrows(
                DomainValidationException.class, () -> service.generate(request));
        assertEquals(2, ex.getErrors().size());
    }

    // ------------------------------------------------------------------
    // Period resolution
    // ------------------------------------------------------------------

    @Test
    @DisplayName("period query criteria carry the request date range")
    void periodQueryCriteriaCarryDateRange() {
        when(payrollPeriodRepository.list(any())).thenReturn(List.of());

        service.generate(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY), false, false));

        ArgumentCaptor<PayrollPeriodCriteria> captor =
                ArgumentCaptor.forClass(PayrollPeriodCriteria.class);
        verify(payrollPeriodRepository).list(captor.capture());
        assertEquals(LocalDate.of(2026, 1, 1), captor.getValue().startDate());
        assertEquals(LocalDate.of(2026, 1, 31), captor.getValue().endDate());
        assertEquals(Integer.MAX_VALUE, captor.getValue().size());
    }

    @Test
    @DisplayName("no periods in range returns empty response")
    void noPeriodsReturnsEmptyResponse() {
        when(payrollPeriodRepository.list(any())).thenReturn(List.of());
        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(), false, false);

        AggregateReportResponse response = service.generate(request);

        assertEquals(ReportKind.AGGREGATE, response.kind());
        assertEquals(REFERENCE_CURRENCY, response.referenceCurrency());
        assertTrue(response.lines().isEmpty());
        assertTrue(response.warnings().isEmpty());
        verify(snapshotBuilder, never()).build(any());
        verify(reportDataRepository, never()).queryAggregates(anyList(), anyList(), any());
    }

    // ------------------------------------------------------------------
    // Snapshots
    // ------------------------------------------------------------------

    @Test
    @DisplayName("all periods closed skips snapshot building")
    void allPeriodsClosedSkipsSnapshotBuilding() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(aggregateRow()));

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), false, false));

        verify(snapshotBuilder, never()).build(any());
        assertEquals(1, response.lines().size());
        assertEquals(REFERENCE_CURRENCY, response.lines().get(0).amount().currency());
    }

    @Test
    @DisplayName("open period builds snapshot and persists")
    void openPeriodBuildsAndPersists() {
        PayrollPeriod period = period(false, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(snapshotBuilder.build(period)).thenReturn(new SnapshotBuildResult(
                List.of(), RankingAndPercentileSnapshot.empty(), List.of(), List.of()));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(aggregateRow()));

        service.generate(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY), false, false));

        verify(snapshotBuilder).build(period);
        verify(payrollPeriodAggregateRepository).deleteDraftsByPayrollPeriodId(period.getId());
        verify(payrollPeriodEarnerRankingRepository).deleteDraftsByPayrollPeriodId(period.getId());
        verify(payrollPeriodEarnerRankingRepository)
                .deleteDraftsPercentileByPayrollPeriodId(period.getId());
        verify(payrollPeriodAggregateRepository).saveAll(anyList());
        verify(payrollPeriodEarnerRankingRepository).saveAll(anyList(), anyList());
    }

    @Test
    @DisplayName("two open periods are both built and persisted")
    void twoOpenPeriodsBothPersisted() {
        PayrollPeriod jan = period(false, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        PayrollPeriod feb = period(false, "February 2026",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(jan, feb));
        when(snapshotBuilder.build(jan)).thenReturn(new SnapshotBuildResult(
                List.of(), RankingAndPercentileSnapshot.empty(), List.of(), List.of()));
        when(snapshotBuilder.build(feb)).thenReturn(new SnapshotBuildResult(
                List.of(), RankingAndPercentileSnapshot.empty(), List.of(), List.of()));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of());

        service.generate(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28),
                List.of(GroupKey.COUNTRY), false, false));

        verify(payrollPeriodAggregateRepository).deleteDraftsByPayrollPeriodId(jan.getId());
        verify(payrollPeriodAggregateRepository).deleteDraftsByPayrollPeriodId(feb.getId());
        verify(payrollPeriodEarnerRankingRepository).deleteDraftsByPayrollPeriodId(jan.getId());
        verify(payrollPeriodEarnerRankingRepository).deleteDraftsByPayrollPeriodId(feb.getId());
        verify(payrollPeriodEarnerRankingRepository)
                .deleteDraftsPercentileByPayrollPeriodId(jan.getId());
        verify(payrollPeriodEarnerRankingRepository)
                .deleteDraftsPercentileByPayrollPeriodId(feb.getId());
        verify(payrollPeriodAggregateRepository, times(2)).saveAll(anyList());
        verify(payrollPeriodEarnerRankingRepository, times(2))
                .saveAll(anyList(), anyList());
    }

    @Test
    @DisplayName("blocker in open period throws ReportBlockedException and does not persist")
    void blockerThrowsAndDoesNotPersist() {
        PayrollPeriod period = period(false, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        ReportWarning blocker = new ReportWarning(
                ReportWarningCode.MISSING_FX_RATE, ReportWarning.Severity.BLOCKER,
                "Missing FX rate", Map.of());
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(snapshotBuilder.build(period)).thenReturn(new SnapshotBuildResult(
                List.of(), RankingAndPercentileSnapshot.empty(), List.of(), List.of(blocker)));

        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY), false, false);

        ReportBlockedException ex = assertThrows(ReportBlockedException.class,
                () -> service.generate(request));
        assertEquals(1, ex.getBlockers().size());
        verify(payrollPeriodAggregateRepository, never()).deleteDraftsByPayrollPeriodId(any());
        verify(payrollPeriodAggregateRepository, never()).saveAll(anyList());
        verify(payrollPeriodEarnerRankingRepository, never()).deleteDraftsByPayrollPeriodId(any());
        verify(payrollPeriodEarnerRankingRepository, never())
                .deleteDraftsPercentileByPayrollPeriodId(any());
        verify(payrollPeriodEarnerRankingRepository, never()).saveAll(anyList(), anyList());
    }

    @Test
    @DisplayName("a blocker on a later period prevents all persistence")
    void laterPeriodBlockerPreventsAllPersistence() {
        PayrollPeriod jan = period(false, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        PayrollPeriod feb = period(false, "February 2026",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        ReportWarning blocker = new ReportWarning(
                ReportWarningCode.MISSING_FX_RATE, ReportWarning.Severity.BLOCKER,
                "Missing FX", Map.of());
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(jan, feb));
        when(snapshotBuilder.build(jan)).thenReturn(new SnapshotBuildResult(
                List.of(), RankingAndPercentileSnapshot.empty(), List.of(), List.of()));
        when(snapshotBuilder.build(feb)).thenReturn(new SnapshotBuildResult(
                List.of(), RankingAndPercentileSnapshot.empty(), List.of(), List.of(blocker)));

        assertThrows(ReportBlockedException.class, () ->
                service.generate(request(LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 2, 28),
                        List.of(GroupKey.COUNTRY), false, false)));

        // Neither period was persisted: not even the one that built cleanly.
        verify(payrollPeriodAggregateRepository, never()).deleteDraftsByPayrollPeriodId(any());
        verify(payrollPeriodAggregateRepository, never()).saveAll(anyList());
        verify(payrollPeriodEarnerRankingRepository, never()).deleteDraftsByPayrollPeriodId(any());
        verify(payrollPeriodEarnerRankingRepository, never())
                .deleteDraftsPercentileByPayrollPeriodId(any());
        verify(payrollPeriodEarnerRankingRepository, never()).saveAll(anyList(), anyList());
    }

    // ------------------------------------------------------------------
    // Filters and breakdown
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the filter is preserved and passed to the query")
    void filterPreservedWhenGrouped() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of());

        UUID countryId = UUID.randomUUID();
        AggregateReportRequest request = new AggregateReportRequest(
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                List.of(GroupKey.COUNTRY),
                Set.of(countryId),
                null, null, null,
                false, false);

        service.generate(request);

        ArgumentCaptor<ReportEffectiveFilters> captor =
                ArgumentCaptor.forClass(ReportEffectiveFilters.class);
        verify(reportDataRepository).queryAggregates(anyList(), anyList(), captor.capture());
        assertEquals(Set.of(countryId), captor.getValue().countryIds());
    }

    @Test
    @DisplayName("breakdown is populated when requested")
    void breakdownPopulatedWhenRequested() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(aggregateRow()));

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), true, false));

        AggregateLineDto line = response.lines().get(0);
        assertEquals(REFERENCE_CURRENCY, line.breakdown().currency());
        assertEquals(0, line.breakdown().total().compareTo(new BigDecimal("1350.00")));
    }

    @Test
    @DisplayName("line employee count is zero when projection has null count")
    void lineEmployeeCountZeroWhenNull() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));

        AggregateRowProjection row = aggregateRow();
        row.employeeCount = null;
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(row));

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), false, false));

        assertEquals(0, response.lines().get(0).employeeCount());
    }

    // ------------------------------------------------------------------
    // Statistics
    // ------------------------------------------------------------------

    @Test
    @DisplayName("monthly statistics populate the monthly block")
    void monthlyStatisticsPopulateMonthlyBlock() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(aggregateRow()));

        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.MONTHLY), any(), any()))
                .thenReturn(List.of(statsRow("1000.00", "9000.00")));
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any()))
                .thenReturn(List.of());

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), false, true));

        AggregateLineDto line = response.lines().get(0);
        assertNotNull(line.statistics());
        assertNotNull(line.statistics().monthly());
        assertNull(line.statistics().biWeekly());
        assertEquals(0, line.statistics().monthly().min().amount()
                .compareTo(new BigDecimal("1000.00")));
        assertEquals(0, line.statistics().monthly().max().amount()
                .compareTo(new BigDecimal("9000.00")));
        // Average is monthlyTotal / monthlyEmployeeCount = 1350.00 / 2 = 675.00.
        assertEquals(0, line.statistics().monthly().average().amount()
                .compareTo(new BigDecimal("675.00")));
    }

    @Test
    @DisplayName("bi-weekly statistics populate the bi-weekly block")
    void biWeeklyStatisticsPopulateBiWeeklyBlock() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        AggregateRowProjection row = aggregateRow();
        row.biWeeklyTotal = new BigDecimal("600.00");
        row.biWeeklyEmployeeCount = 2L;
        row.biWeeklyPeriodCount = 1L;
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(row));

        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.MONTHLY), any(), any()))
                .thenReturn(List.of());
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any()))
                .thenReturn(List.of(statsRow("300.00", "500.00")));

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), false, true));

        AggregateLineDto line = response.lines().get(0);
        assertNotNull(line.statistics());
        assertNull(line.statistics().monthly());
        assertNotNull(line.statistics().biWeekly());
        assertEquals(0, line.statistics().biWeekly().min().amount()
                .compareTo(new BigDecimal("300.00")));
        assertEquals(0, line.statistics().biWeekly().max().amount()
                .compareTo(new BigDecimal("500.00")));
        // Average is biWeeklyTotal / biWeeklyEmployeeCount = 600.00 / 2 = 300.00.
        assertEquals(0, line.statistics().biWeekly().average().amount()
                .compareTo(new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("both blocks are populated when the group has both frequencies")
    void bothBlocksPopulatedWhenBothFrequenciesPresent() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        AggregateRowProjection row = aggregateRow();
        row.biWeeklyTotal = new BigDecimal("600.00");
        row.biWeeklyEmployeeCount = 2L;
        row.biWeeklyPeriodCount = 1L;
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(row));

        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.MONTHLY), any(), any()))
                .thenReturn(List.of(statsRow("1000.00", "9000.00")));
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any()))
                .thenReturn(List.of(statsRow("300.00", "500.00")));

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), false, true));

        AggregateLineDto line = response.lines().get(0);
        assertNotNull(line.statistics());
        assertNotNull(line.statistics().monthly());
        assertNotNull(line.statistics().biWeekly());
        assertEquals(0, line.statistics().monthly().average().amount()
                .compareTo(new BigDecimal("675.00")));
        assertEquals(0, line.statistics().biWeekly().average().amount()
                .compareTo(new BigDecimal("300.00")));
    }

    @Test
    @DisplayName("statistics query is skipped when includeStatistics is false")
    void statisticsQuerySkippedWhenNotRequested() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(aggregateRow()));

        service.generate(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY), false, false));

        verify(reportDataRepository, never()).queryStatistics(
                anyList(), anyList(), any(), any(), any());
    }

    @Test
    @DisplayName("average is null when employeeCount is zero")
    void averageNullWhenEmployeesZero() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));

        AggregateRowProjection row = aggregateRow();
        row.monthlyTotal = BigDecimal.ZERO;
        row.monthlyEmployeeCount = 0L;
        row.monthlyPeriodCount = 0L;
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(row));
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.MONTHLY), any(), any()))
                .thenReturn(List.of(statsRow("1000.00", "9000.00")));
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any()))
                .thenReturn(List.of());

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), false, true));

        assertNull(response.lines().get(0).statistics().monthly().average());
        assertEquals(0, response.lines().get(0).statistics().monthly().employeeCount());
        assertEquals(0, response.lines().get(0).statistics().monthly().periodCount());
    }

    @Test
    @DisplayName("statistics attach to the line keyed by DEPARTMENT when grouping by department")
    void statisticsAttachForKeyDepartment() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));

        AggregateRowProjection row = aggregateRow();
        row.country = null;
        row.department = new RefDto(UUID.randomUUID(), "ENG", "Engineering");
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(row));

        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.MONTHLY), any(), any()))
                .thenReturn(List.of(statsRowForDepartment(
                        row.department.id(), "1000.00", "9000.00")));
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any()))
                .thenReturn(List.of());

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.DEPARTMENT), false, true));

        AggregateLineDto line = response.lines().get(0);
        assertNotNull(line.statistics());
        assertNotNull(line.statistics().monthly());
    }

    @Test
    @DisplayName("statistics attach to the line keyed by ROLE when grouping by role")
    void statisticsAttachForKeyRole() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));

        AggregateRowProjection row = aggregateRow();
        row.country = null;
        row.role = new RefDto(UUID.randomUUID(), "SWE", "Software Engineer");
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(row));

        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.MONTHLY), any(), any()))
                .thenReturn(List.of(statsRowForRole(
                        row.role.id(), "1000.00", "9000.00")));
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any()))
                .thenReturn(List.of());

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.ROLE), false, true));

        AggregateLineDto line = response.lines().get(0);
        assertNotNull(line.statistics());
        assertNotNull(line.statistics().monthly());
    }

    @Test
    @DisplayName("statistics attach to the line keyed by EMPLOYMENT_TYPE")
    void statisticsAttachForKeyEmploymentType() {
        PayrollPeriod period = period(true, "January 2026",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));

        AggregateRowProjection row = aggregateRow();
        row.country = null;
        row.employmentType = com.salary.management.domain.model.enums.EmploymentType.FULL_TIME;
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(row));

        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.MONTHLY), any(), any()))
                .thenReturn(List.of(statsRowForEmploymentType(
                        row.employmentType, "1000.00", "9000.00")));
        when(reportDataRepository.queryStatistics(
                anyList(), anyList(), eq(PayrollPeriodFrequency.BI_WEEKLY), any(), any()))
                .thenReturn(List.of());

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.EMPLOYMENT_TYPE), false, true));

        AggregateLineDto line = response.lines().get(0);
        assertNotNull(line.statistics());
        assertNotNull(line.statistics().monthly());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private AggregateReportRequest request(LocalDate start, LocalDate end,
                                           List<GroupKey> groupKeys, boolean breakdown,
                                           boolean statistics) {
        return new AggregateReportRequest(
                new DateRange(start, end), groupKeys,
                null, null, null, null, breakdown, statistics);
    }

    private PayrollPeriod period(boolean closed, String name,
                                 LocalDate start, LocalDate end) {
        return PayrollPeriod.builder()
                .id(UUID.randomUUID())
                .name(name)
                .startDate(start)
                .endDate(end)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(closed)
                .build();
    }

    private AggregateRowProjection aggregateRow() {
        AggregateRowProjection p = new AggregateRowProjection();
        p.country = new RefDto(FR_UUID, "FR", "France");
        p.sumBase = new BigDecimal("1000.00");
        p.sumVariable = new BigDecimal("100.00");
        p.sumAllowances = new BigDecimal("50.00");
        p.sumEmployerTaxes = new BigDecimal("200.00");
        p.sumTotal = new BigDecimal("1350.00");
        p.sumEmployeeCount = 2L;
        p.monthlyTotal = new BigDecimal("1350.00");
        p.monthlyEmployeeCount = 2L;
        p.monthlyPeriodCount = 1L;
        p.biWeeklyTotal = BigDecimal.ZERO;
        p.biWeeklyEmployeeCount = 0L;
        p.biWeeklyPeriodCount = 0L;
        p.employeeCount = 2L;
        return p;
    }

    private AggregateRowProjection statsRow(String min, String max) {
        AggregateRowProjection p = new AggregateRowProjection();
        p.country = new RefDto(FR_UUID, "FR", "France");
        p.minReferenceAmount = new BigDecimal(min);
        p.maxReferenceAmount = new BigDecimal(max);
        return p;
    }

    private AggregateRowProjection statsRowForDepartment(UUID id, String min, String max) {
        AggregateRowProjection p = new AggregateRowProjection();
        p.department = new RefDto(id, "ENG", "Engineering");
        p.minReferenceAmount = new BigDecimal(min);
        p.maxReferenceAmount = new BigDecimal(max);
        return p;
    }

    private AggregateRowProjection statsRowForRole(UUID id, String min, String max) {
        AggregateRowProjection p = new AggregateRowProjection();
        p.role = new RefDto(id, "SWE", "Software Engineer");
        p.minReferenceAmount = new BigDecimal(min);
        p.maxReferenceAmount = new BigDecimal(max);
        return p;
    }

    private AggregateRowProjection statsRowForEmploymentType(
            com.salary.management.domain.model.enums.EmploymentType type,
            String min, String max) {
        AggregateRowProjection p = new AggregateRowProjection();
        p.employmentType = type;
        p.minReferenceAmount = new BigDecimal(min);
        p.maxReferenceAmount = new BigDecimal(max);
        return p;
    }
}