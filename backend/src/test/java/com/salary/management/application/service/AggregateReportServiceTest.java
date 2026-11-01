package com.salary.management.application.service;

import com.salary.management.application.dto.aggregate.AggregateLineDto;
import com.salary.management.application.dto.aggregate.AggregateReportRequest;
import com.salary.management.application.dto.aggregate.AggregateReportResponse;
import com.salary.management.application.dto.DateRange;
import com.salary.management.application.dto.RefDto;
import com.salary.management.application.exception.ReportBlockedException;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.ReportKind;
import com.salary.management.domain.model.enums.ReportWarningCode;
import com.salary.management.domain.model.values.RankingAndPercentileSnapshot;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.model.values.SnapshotBuildResult;
import com.salary.management.domain.repository.*;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.domain.repository.criteria.ReportEffectiveFilters;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private PayrollPeriodSnapshotRepository payrollPeriodSnapshotRepository;

    @Mock
    private CurrencyRateRepository currencyRateRepository;

    @Mock
    private EntityManager entityManager;

    private AggregateReportService service;

    private UUID FR_UUID;

    @BeforeEach
    void setup() {
        FR_UUID = UUID.randomUUID();
        service = new AggregateReportService(reportDataRepository, currencyRateRepository, payrollPeriodRepository, payrollPeriodSnapshotRepository);
    }

    // ------------------------------------------------------------------
    // Validation — the reference-currency stub is NOT consumed here,
    // because every test fails during validate() before reaching it.
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
    // Period resolution — emptyResponse reads referenceCurrency()
    // ------------------------------------------------------------------

    @Test
    @DisplayName("period query criteria carry the request date range")
    void periodQueryCriteriaCarryDateRange() {
        when(currencyRateRepository.getReferenceCurrencyCode()).thenReturn(REFERENCE_CURRENCY);
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
    // ------------------------------------------------------------------
    // Filters and breakdown
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the filter is preserved and passed to the query")
    void filterPreservedWhenGrouped() {
        when(currencyRateRepository.getReferenceCurrencyCode()).thenReturn(REFERENCE_CURRENCY);
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
        when(currencyRateRepository.getReferenceCurrencyCode()).thenReturn(REFERENCE_CURRENCY);
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
        when(currencyRateRepository.getReferenceCurrencyCode()).thenReturn(REFERENCE_CURRENCY);
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