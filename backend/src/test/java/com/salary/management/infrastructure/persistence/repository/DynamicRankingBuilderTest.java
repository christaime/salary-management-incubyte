package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link DynamicRankingBuilder} with a mocked EntityManager. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DynamicRankingBuilderTest {

    private static final UUID JANUARY =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENGINEERING =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ENGINEER =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID GRACE =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01");
    private static final UUID KATHERINE =
            UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03");

    private static final String REF_CURRENCY = "USD";

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Tuple> query;

    /**
     * The percentile query is native SQL, so it goes through
     * {@link EntityManager#createNativeQuery(String)} and returns a
     * {@link Query}, not a {@link TypedQuery}. Stub it so the mocked
     * EntityManager does not return {@code null} and NPE.
     */
    @Mock
    private Query nativeQuery;

    @InjectMocks
    private DynamicRankingBuilder builder;

    // ------------------------------------------------------------------
    // Public entry-point guards
    // ------------------------------------------------------------------

    @Test
    @DisplayName("null period is rejected")
    void nullPeriodIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                builder.rankAndComputePercentilesForPeriod(
                        null, REF_CURRENCY, GroupKey.COUNTRY, 5));
    }

    @Test
    @DisplayName("CURRENCY is rejected as a grouping key")
    void currencyKeyIsRejected() {
        // The first rank() call fails because CURRENCY is not a valid
        // selectDimensions / partitionBy / groupByDimensions key.
        assertThrows(IllegalArgumentException.class, () ->
                builder.rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.CURRENCY, 5));
    }

    // ------------------------------------------------------------------
    // Query-shape assertions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("global ranking has no PARTITION BY clause")
    void globalHasNoPartitionBy() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, null, 5);

        String jpql = captureFirstJpql();
        assertFalse(jpql.contains("PARTITION BY"));
        assertTrue(jpql.contains("ROW_NUMBER() OVER ("));
        assertTrue(jpql.contains("r.rank <= :limit"));
        assertTrue(jpql.contains("s.payrollPeriod.startDate >= :startDate"));
        assertTrue(jpql.contains("s.payrollPeriod.startDate < :endDate"));
    }

    @Test
    @DisplayName("country ranking partitions by country")
    void countryPartition() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5);

        String jpql = captureFirstJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentCountryId"));
        assertTrue(jpql.contains("r.countryId"));
        assertFalse(jpql.contains("r.departmentId"));
    }

    @Test
    @DisplayName("department ranking partitions by department")
    void departmentPartition() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, GroupKey.DEPARTMENT, 5);

        String jpql = captureFirstJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentDepartmentId"));
        assertTrue(jpql.contains("r.departmentId"));
        assertFalse(jpql.contains("r.countryId"));
    }

    @Test
    @DisplayName("role ranking partitions by role")
    void rolePartition() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, GroupKey.ROLE, 5);

        String jpql = captureFirstJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentRoleId"));
        assertTrue(jpql.contains("r.roleId"));
    }

    @Test
    @DisplayName("employment type ranking partitions by employment type")
    void employmentTypePartition() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, GroupKey.EMPLOYMENT_TYPE, 5);

        String jpql = captureFirstJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentEmploymentType"));
        assertTrue(jpql.contains("r.employmentType"));
    }

    @Test
    @DisplayName("TOP orders by monthly rate descending, BOTTOM ascending")
    void directionControlsOrder() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5);

        // Two JPQL ranking queries: TOP then BOTTOM. The percentile query is
        // native SQL and goes through createNativeQuery, not createQuery, so
        // it is not counted here.
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, times(2))
                .createQuery(captor.capture(), eq(Tuple.class));

        List<String> jpqls = captor.getAllValues();
        assertEquals(2, jpqls.size());

        String topJpql    = jpqls.get(0);
        String bottomJpql = jpqls.get(1);

        assertTrue(topJpql.contains("END DESC"));
        assertTrue(bottomJpql.contains("END ASC"));
    }

    @Test
    @DisplayName("ranking query orders by monthly rate, not raw amount")
    void rankingOrdersByMonthlyRate() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5);

        String jpql = captureFirstJpql();
        // The ORDER BY uses the derived monthlyRate expression, not raw reference total
        assertTrue(jpql.contains("CASE"));
        assertTrue(jpql.contains("/"));
        assertTrue(jpql.contains("* :daysInMonth"));
    }

    @Test
    @DisplayName("ranking query covers bi-weekly employees via daysCovered")
    void rankingQueryCoversBiweekly() {
        stubEmptyQuery();

        builder.rankAndComputePercentilesForPeriod(
                aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5);

        String jpql = captureFirstJpql();
        assertTrue(jpql.contains("WHEN p.frequency = 'BI_WEEKLY' THEN :daysInBiweekly"));
        assertTrue(jpql.contains("WHEN p.frequency = 'MONTHLY'   THEN :daysInMonth"));
    }

    // ------------------------------------------------------------------
    // Entity mapping — one test per partition kind.
    //
    // The stub returns the same ranking row for both TOP and BOTTOM queries,
    // so rankings() contains 2 rows (one TOP, one BOTTOM).
    // ------------------------------------------------------------------

    @Test
    @DisplayName("COUNTRY row sets countryId only")
    void countryRowSetsOnlyCountry() {
        Tuple row = rankingTuple()
                .withCountry(FR)
                .withEmployee(GRACE)
                .withRank(1)
                .withDaysCovered(31)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .withMonthlyRate("2500.00", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5)
                .rankings();

        // One TOP + one BOTTOM
        assertEquals(2, rows.size());
        PayrollPeriodEarnerRankingEntity e = rows.get(0);
        assertEquals(FR, e.getCountryId());
        assertNull(e.getDepartmentId());
        assertNull(e.getRoleId());
        assertNull(e.getEmploymentType());
        assertEquals(EarnerRankingDirection.TOP, e.getDirection());
        assertEquals(GRACE, e.getEmployeeId());
        assertEquals(1, e.getRank());
        assertEquals(0, e.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("2700.00")));
        assertEquals("EUR", e.getOriginalAmount().getCurrency());
        assertEquals(0, e.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("2500.00")));
        assertEquals("USD", e.getReferenceAmount().getCurrency());
        assertEquals(31, e.getDaysCovered());
        assertEquals(0, e.getMonthlyRateAmount().getAmount()
                .compareTo(new BigDecimal("2500.00")));
        assertEquals("USD", e.getMonthlyRateAmount().getCurrency());
        assertEquals(JANUARY, e.getPayrollPeriodId());
        assertEquals(PayrollSummaryStatus.VALIDATED, e.getStatus());

        // The second row is the BOTTOM counterpart
        PayrollPeriodEarnerRankingEntity bottom = rows.get(1);
        assertEquals(EarnerRankingDirection.BOTTOM, bottom.getDirection());
        assertEquals(FR, bottom.getCountryId());
    }

    @Test
    @DisplayName("DEPARTMENT row sets departmentId only")
    void departmentRowSetsOnlyDepartment() {
        Tuple row = rankingTuple()
                .withDepartment(ENGINEERING)
                .withEmployee(GRACE)
                .withRank(1)
                .withDaysCovered(31)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .withMonthlyRate("2500.00", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.DEPARTMENT, 5)
                .rankings();

        assertEquals(2, rows.size());
        PayrollPeriodEarnerRankingEntity e = rows.get(0);
        assertNull(e.getCountryId());
        assertEquals(ENGINEERING, e.getDepartmentId());
        assertNull(e.getRoleId());
        assertNull(e.getEmploymentType());
    }

    @Test
    @DisplayName("ROLE row sets roleId only")
    void roleRowSetsOnlyRole() {
        Tuple row = rankingTuple()
                .withRole(ENGINEER)
                .withEmployee(GRACE)
                .withRank(1)
                .withDaysCovered(31)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .withMonthlyRate("2500.00", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.ROLE, 5)
                .rankings();

        assertEquals(2, rows.size());
        PayrollPeriodEarnerRankingEntity e = rows.get(0);
        assertNull(e.getCountryId());
        assertNull(e.getDepartmentId());
        assertEquals(ENGINEER, e.getRoleId());
        assertNull(e.getEmploymentType());
    }

    @Test
    @DisplayName("EMPLOYMENT_TYPE row sets employmentType only")
    void employmentTypeRowSetsOnlyType() {
        Tuple row = rankingTuple()
                .withEmploymentType(EmploymentType.FULL_TIME)
                .withEmployee(GRACE)
                .withRank(1)
                .withDaysCovered(31)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .withMonthlyRate("2500.00", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.EMPLOYMENT_TYPE, 5)
                .rankings();

        assertEquals(2, rows.size());
        PayrollPeriodEarnerRankingEntity e = rows.get(0);
        assertNull(e.getCountryId());
        assertNull(e.getDepartmentId());
        assertNull(e.getRoleId());
        assertEquals(EmploymentType.FULL_TIME, e.getEmploymentType());
    }

    @Test
    @DisplayName("GLOBAL row has no dimensions")
    void globalRowHasNoDimensions() {
        Tuple row = rankingTuple()
                .withEmployee(GRACE)
                .withRank(1)
                .withDaysCovered(31)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .withMonthlyRate("2500.00", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, null, 5)
                .rankings();

        assertEquals(2, rows.size());
        PayrollPeriodEarnerRankingEntity e = rows.get(0);
        assertNull(e.getCountryId());
        assertNull(e.getDepartmentId());
        assertNull(e.getRoleId());
        assertNull(e.getEmploymentType());
        assertNotNull(e.getReferenceAmount());
        assertNotNull(e.getMonthlyRateAmount());
    }

    @Test
    @DisplayName("BOTTOM direction is written to the entity")
    void bottomDirectionIsWritten() {
        Tuple row = rankingTuple()
                .withCountry(FR)
                .withEmployee(KATHERINE)
                .withRank(1)
                .withDaysCovered(31)
                .withOriginal("1080.00", "EUR")
                .withReference("1000.00", "USD")
                .withMonthlyRate("1000.00", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5)
                .rankings();

        // One TOP + one BOTTOM, both with the same tuple data
        assertEquals(2, rows.size());
        assertEquals(EarnerRankingDirection.TOP, rows.get(0).getDirection());
        assertEquals(EarnerRankingDirection.BOTTOM, rows.get(1).getDirection());
    }

    @Test
    @DisplayName("bi-weekly row preserves daysCovered and monthly rate")
    void biweeklyRowPreservesDaysCoveredAndMonthlyRate() {
        // Ariana's case: 120 USD over 28 days in a 31-day month -> monthly rate 132.86
        Tuple row = rankingTuple()
                .withCountry(FR)
                .withEmployee(GRACE)
                .withRank(2)
                .withDaysCovered(28)
                .withOriginal("120.00", "USD")
                .withReference("120.00", "USD")
                .withMonthlyRate("132.86", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5)
                .rankings();

        assertEquals(2, rows.size());
        PayrollPeriodEarnerRankingEntity e = rows.get(0);
        assertEquals(28, e.getDaysCovered());
        assertEquals(0, e.getMonthlyRateAmount().getAmount()
                .compareTo(new BigDecimal("132.86")));
        assertEquals("USD", e.getMonthlyRateAmount().getCurrency());
    }

    @Test
    @DisplayName("rank is read from the tuple")
    void rankIsReadFromTuple() {
        Tuple row = rankingTuple()
                .withCountry(FR)
                .withEmployee(GRACE)
                .withRank(3)
                .withDaysCovered(31)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .withMonthlyRate("2500.00", "USD")
                .build();
        stubSingleRankingRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder
                .rankAndComputePercentilesForPeriod(
                        aPayrollPeriod(), REF_CURRENCY, GroupKey.COUNTRY, 5)
                .rankings();

        assertEquals(2, rows.size());
        assertEquals(3, rows.get(0).getRank());
        assertEquals(3, rows.get(1).getRank());
    }

    // ------------------------------------------------------------------
    // Helpers
    //
    // The percentile query is now native SQL, so it goes through
    // createNativeQuery(...) and returns a plain Query. Both stub helpers
    // must stub it so the mocked EntityManager does not return null.
    // Percentile correctness is covered end-to-end in SnapshotBuilderTest.
    // ------------------------------------------------------------------

    private PayrollPeriodEntity aPayrollPeriod() {
        PayrollPeriodEntity period = new PayrollPeriodEntity();
        period.setId(JANUARY);
        period.setStartDate(LocalDate.of(2025, 1, 1));
        period.setEndDate(LocalDate.of(2025, 1, 31));
        period.setFrequency(PayrollPeriodFrequency.MONTHLY);
        return period;
    }

    private void stubEmptyQuery() {
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        stubEmptyNativeQuery();
    }

    /**
     * Stubs the same ranking row for both JPQL queries (TOP, BOTTOM).
     * {@code rankings()} contains 2 rows; the native percentile query
     * returns an empty result, so {@code percentiles()} is empty.
     */
    private void stubSingleRankingRow(Tuple row) {
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));
        stubEmptyNativeQuery();
    }

    private void stubEmptyNativeQuery() {
        when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
        when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
        when(nativeQuery.getResultList()).thenReturn(List.of());
    }

    /**
     * Captures all JPQL strings that flow through {@code createQuery(..., Tuple.class)}
     * and returns the first one — the TOP ranking query, which is the subject of the
     * query-shape assertions.
     */
    private String captureFirstJpql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce())
                .createQuery(captor.capture(), eq(Tuple.class));
        return captor.getAllValues().get(0);
    }

    private RankingTupleBuilder rankingTuple() {
        return new RankingTupleBuilder();
    }

    // ------------------------------------------------------------------
    // Tuple builder (ranking only — the percentile fixtures moved to
    // SnapshotBuilderTest where a real SQLite DB validates the SQL)
    // ------------------------------------------------------------------

    private static final class RankingTupleBuilder {

        private final Tuple tuple = mock(Tuple.class);

        RankingTupleBuilder withCountry(UUID countryId) {
            when(tuple.get("countryId", UUID.class)).thenReturn(countryId);
            return this;
        }

        RankingTupleBuilder withDepartment(UUID departmentId) {
            when(tuple.get("departmentId", UUID.class)).thenReturn(departmentId);
            return this;
        }

        RankingTupleBuilder withRole(UUID roleId) {
            when(tuple.get("roleId", UUID.class)).thenReturn(roleId);
            return this;
        }

        RankingTupleBuilder withEmploymentType(EmploymentType employmentType) {
            when(tuple.get("employmentType", EmploymentType.class)).thenReturn(employmentType);
            return this;
        }

        RankingTupleBuilder withEmployee(UUID employeeId) {
            when(tuple.get("employeeId", UUID.class)).thenReturn(employeeId);
            return this;
        }

        RankingTupleBuilder withRank(int rank) {
            when(tuple.get("rank")).thenReturn(rank);
            return this;
        }

        RankingTupleBuilder withDaysCovered(int daysCovered) {
            when(tuple.get("daysCovered")).thenReturn(daysCovered);
            return this;
        }

        RankingTupleBuilder withOriginal(String total, String currency) {
            when(tuple.get("originalTotal", BigDecimal.class))
                    .thenReturn(new BigDecimal(total));
            when(tuple.get("originalCurrency", String.class)).thenReturn(currency);
            return this;
        }

        RankingTupleBuilder withReference(String total, String currency) {
            when(tuple.get("referenceTotal", BigDecimal.class))
                    .thenReturn(new BigDecimal(total));
            when(tuple.get("referenceCurrency", String.class)).thenReturn(currency);
            return this;
        }

        RankingTupleBuilder withMonthlyRate(String total, String currency) {
            when(tuple.get("monthlyRate", BigDecimal.class))
                    .thenReturn(new BigDecimal(total));
            when(tuple.get("referenceCurrency", String.class)).thenReturn(currency);
            return this;
        }

        RankingTupleBuilder withPeriodId(UUID periodId) {
            when(tuple.get("periodId", UUID.class)).thenReturn(periodId);
            return this;
        }

        Tuple build() {
            // Default periodId so toRankingEntity doesn't NPE on
            // t.get("periodId", UUID.class) — every ranking query selects r.periodId.
            when(tuple.get("periodId", UUID.class)).thenReturn(JANUARY);
            return tuple;
        }
    }
}