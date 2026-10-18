package com.salary.management.application.service;

import com.salary.management.application.dto.AggregateLineDto;
import com.salary.management.application.dto.AggregateReportRequest;
import com.salary.management.application.dto.AggregateReportResponse;
import com.salary.management.application.dto.DateRange;
import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.application.snapshot.SnapshotBuilder;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.enums.ReportWarningCode;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.model.values.SnapshotBuildResult;
import com.salary.management.domain.repository.CurrencyRateRepository;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.ReportDataRepository;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
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

    @BeforeEach
    void setupReferenceCurrency() {
        when(currencyRateRepository.getReferenceCurrencyCode()).thenReturn(REFERENCE_CURRENCY);
    }

    @Test
    @DisplayName("reversed date range throws DomainValidationException")
    void reversedDateRangeThrows() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1),
                List.of(), false, false);

        assertThrows(DomainValidationException.class, () -> service.generate(request));
    }

    @Test
    @DisplayName("ORIGIN_COUNTRY in groupKeys throws DomainValidationException")
    void originCountryGroupKeyThrows() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.ORIGIN_COUNTRY), false, false);

        assertThrows(DomainValidationException.class, () -> service.generate(request));
    }

    @Test
    @DisplayName("includeStatistics with CURRENCY group key throws")
    void statisticsWithCurrencyGroupKeyThrows() {
        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.CURRENCY), false, true);

        assertThrows(DomainValidationException.class, () -> service.generate(request));
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

    @Test
    @DisplayName("all periods closed skips snapshot building")
    void allPeriodsClosedSkipsSnapshotBuilding() {
        PayrollPeriod period = period(true);
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
        PayrollPeriod period = period(false);
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(snapshotBuilder.build(period)).thenReturn(new SnapshotBuildResult(
                List.of(), List.of(), List.of(), List.of()));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(aggregateRow()));

        service.generate(request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY), false, false));

        verify(snapshotBuilder).build(period);
        verify(payrollPeriodAggregateRepository).deleteDraftsByPayrollPeriodId(period.getId());
        verify(payrollPeriodEarnerRankingRepository).deleteDraftsByPayrollPeriodId(period.getId());
        verify(payrollPeriodAggregateRepository).saveAll(anyList());
        verify(payrollPeriodEarnerRankingRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("blocker in open period throws ReportBlockedException and does not persist")
    void blockerThrowsAndDoesNotPersist() {
        PayrollPeriod period = period(false);
        ReportWarning blocker = new ReportWarning(
                ReportWarningCode.MISSING_FX_RATE, ReportWarning.Severity.BLOCKER,
                "Missing FX rate", Map.of());
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(snapshotBuilder.build(period)).thenReturn(new SnapshotBuildResult(
                List.of(), List.of(), List.of(), List.of(blocker)));

        AggregateReportRequest request = request(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                List.of(GroupKey.COUNTRY), false, false);

        ReportBlockedException ex = assertThrows(ReportBlockedException.class,
                () -> service.generate(request));
        assertEquals(1, ex.getBlockers().size());
        verify(payrollPeriodAggregateRepository, never()).deleteDraftsByPayrollPeriodId(any());
        verify(payrollPeriodAggregateRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("filter is dropped when the dimension is a group key")
    void filterDroppedWhenGrouped() {
        PayrollPeriod period = period(true);
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of());

        UUID countryId = UUID.randomUUID();
        AggregateReportRequest request = new AggregateReportRequest(
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                List.of(GroupKey.COUNTRY),
                java.util.Set.of(countryId),
                null, null, null,
                false, false);

        service.generate(request);

        ArgumentCaptor<com.salary.management.domain.repository.criteria.ReportEffectiveFilters>
                captor = ArgumentCaptor.forClass(
                com.salary.management.domain.repository.criteria.ReportEffectiveFilters.class);
        verify(reportDataRepository).queryAggregates(anyList(), anyList(), captor.capture());
        assertNull(captor.getValue().countryIds());
    }

    @Test
    @DisplayName("breakdown is populated when requested")
    void breakdownPopulatedWhenRequested() {
        PayrollPeriod period = period(true);
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
    @DisplayName("statistics populated from rankings with TOP and BOTTOM")
    void statisticsPopulatedFromRankings() {
        PayrollPeriod period = period(true);
        when(payrollPeriodRepository.list(any())).thenReturn(List.of(period));
        when(reportDataRepository.queryAggregates(anyList(), anyList(), any()))
                .thenReturn(List.of(aggregateRow()));

        AggregateRowProjection top = statisticsRow("TOP", new BigDecimal("9000.00"));
        AggregateRowProjection bottom = statisticsRow("BOTTOM", new BigDecimal("1000.00"));
        when(reportDataRepository.queryStatistics(anyList(), anyList(), any()))
                .thenReturn(List.of(top, bottom));

        AggregateReportResponse response = service.generate(
                request(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31),
                        List.of(GroupKey.COUNTRY), false, true));

        AggregateLineDto line = response.lines().get(0);
        assertEquals(0, line.statistics().min().amount().compareTo(new BigDecimal("1000.00")));
        assertEquals(0, line.statistics().max().amount().compareTo(new BigDecimal("9000.00")));
        assertEquals(0, line.statistics().average().amount()
                .compareTo(new BigDecimal("675.00")));
    }

    private AggregateReportRequest request(LocalDate start, LocalDate end,
                                           List<GroupKey> groupKeys, boolean breakdown, boolean statistics) {
        return new AggregateReportRequest(
                new DateRange(start, end), groupKeys,
                null, null, null, null, breakdown, statistics);
    }

    private PayrollPeriod period(boolean closed) {
        return PayrollPeriod.builder()
                .id(UUID.randomUUID())
                .name("January 2026")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(closed)
                .build();
    }

    private AggregateRowProjection aggregateRow() {
        AggregateRowProjection p = new AggregateRowProjection();
        p.country = new com.salary.management.application.dto.RefDto("FR", "France");
        p.sumBase = new BigDecimal("1000.00");
        p.sumVariable = new BigDecimal("100.00");
        p.sumAllowances = new BigDecimal("50.00");
        p.sumEmployerTaxes = new BigDecimal("200.00");
        p.sumTotal = new BigDecimal("1350.00");
        p.sumEmployeeCount = 2L;
        return p;
    }

    private AggregateRowProjection statisticsRow(String direction, BigDecimal amount) {
        AggregateRowProjection p = new AggregateRowProjection();
        p.country = new com.salary.management.application.dto.RefDto("FR", "France");
        p.direction = direction;
        p.amount = amount;
        return p;
    }
}