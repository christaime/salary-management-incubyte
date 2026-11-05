package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link DynamicStatisticsBuilder}.
 *
 * <p>Scope is the read path used by the reports:</p>
 * <ul>
 *   <li>{@code minMax(...)} — per-partition min/max of VALIDATED summary totals,
 *       converted to a reference currency.</li>
 * </ul>
 *
 * <p>Fixture: {@code /sql/statistics-base.sql} supplies seven payroll periods; each
 * test selects its slice by date range, {@link GroupKey}, and filters. Every fixture
 * row is part of one of those slices, and each slice contains noise the query must
 * exclude (DRAFT rows, unconvertible currencies, employees outside the date range).</p>
 *
 * <p>Two methods ({@code E1}, {@code E13}) add a second script,
 * {@code /sql/statistics-unconvertible.sql}, that appends the P8 and P9 periods
 * without touching the base data. The base script always runs first.</p>
 *
 * <p>The {@code @Sql} scripts are executed before each test method; combined with
 * {@code @Transactional}, every test runs against a freshly seeded, isolated database.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/statistics-base.sql")
class DynamicStatisticsBuilderTest {

    private static final UUID DEU = UUID.fromString("00000000-0000-4000-a000-000000000102");
    private static final UUID GBR = UUID.fromString("00000000-0000-4000-a000-000000000103");
    private static final UUID ENG = UUID.fromString("00000000-0000-4000-a000-000000000201");
    private static final UUID SDE = UUID.fromString("00000000-0000-4000-a000-000000000301");

    @Autowired
    private DynamicStatisticsBuilder builder;

    // ------------------------------------------------------------------
    // E7a / E7b — frequency selects which frequency fields are populated
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E7a — MONTHLY frequency populates monthly* fields and leaves biWeekly* null")
    void monthlyFrequencyPopulatesMonthlyFieldsOnly() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(1, result.size());
        AggregateRowProjection p = result.get(0);

        assertEquals(0, p.monthlyTotal.compareTo(new BigDecimal("65000.00")));
        assertEquals(Long.valueOf(11), p.monthlyEmployeeCount);
        assertEquals(Long.valueOf(1), p.monthlyPeriodCount);

        assertNull(p.biWeeklyTotal);
        assertNull(p.biWeeklyEmployeeCount);
        assertNull(p.biWeeklyPeriodCount);

        assertEquals(0, p.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, p.maxReferenceAmount.compareTo(new BigDecimal("10000.00")));
    }

    @Test
    @DisplayName("E7b — BI_WEEKLY frequency populates biWeekly* fields and leaves monthly* null")
    void biWeeklyFrequencyPopulatesBiWeeklyFieldsOnly() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-30"),
                null,
                PayrollPeriodFrequency.BI_WEEKLY,
                "USD",
                null, null, null, null);

        assertEquals(1, result.size());
        AggregateRowProjection p = result.get(0);

        assertEquals(0, p.biWeeklyTotal.compareTo(new BigDecimal("53000.00")));
        assertEquals(Long.valueOf(8), p.biWeeklyEmployeeCount);
        assertEquals(Long.valueOf(1), p.biWeeklyPeriodCount);

        assertNull(p.monthlyTotal);
        assertNull(p.monthlyEmployeeCount);
        assertNull(p.monthlyPeriodCount);

        assertEquals(0, p.minReferenceAmount.compareTo(new BigDecimal("5000.00")));
        assertEquals(0, p.maxReferenceAmount.compareTo(new BigDecimal("8000.00")));
    }

    // ------------------------------------------------------------------
    // E10 — GroupKey = null collapses the population into one row
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E10 — GroupKey = null returns a single global projection with null dimensions")
    void nullGroupKeyReturnsSingleGlobalProjection() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(1, result.size());
        AggregateRowProjection p = result.get(0);
        assertNull(p.country);
        assertNull(p.department);
        assertNull(p.role);
        assertNull(p.employmentType);
    }

    // ------------------------------------------------------------------
    // E5 / E6 — empty result for empty and DRAFT-only periods
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E5 — a period with no VALIDATED summaries returns an empty list")
    void emptyPeriodReturnsEmptyList() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-04-01"),
                LocalDate.parse("2026-04-30"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("E6 — DRAFT-only summaries are excluded, yielding an empty list")
    void draftOnlyPeriodReturnsEmptyList() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-05-01"),
                LocalDate.parse("2026-05-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertTrue(result.isEmpty());
    }

    // ------------------------------------------------------------------
    // E8 / E11 — filters and distinct counts
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E8 — employeeCount and periodCount are distinct counts on the filtered population")
    void distinctEmployeeAndPeriodCountsOnFilteredPopulation() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                List.of(DEU),
                null, null, null);

        assertEquals(1, result.size());
        AggregateRowProjection p = result.get(0);

        assertEquals(Long.valueOf(4), p.monthlyEmployeeCount);
        assertEquals(Long.valueOf(1), p.monthlyPeriodCount);
        assertEquals(0, p.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, p.maxReferenceAmount.compareTo(new BigDecimal("4000.00")));
        assertEquals(0, p.monthlyTotal.compareTo(new BigDecimal("10000.00")));
    }

    @Test
    @DisplayName("E11 — filters narrow the population (country only, then all four dimensions)")
    void filtersNarrowThePopulation() {
        List<AggregateRowProjection> countryOnly = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                List.of(DEU), null, null, null);

        List<AggregateRowProjection> allFour = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                List.of(DEU),
                List.of(ENG),
                List.of(SDE),
                List.of(EmploymentType.FULL_TIME));

        assertEquals(1, countryOnly.size());
        assertEquals(Long.valueOf(4), countryOnly.get(0).monthlyEmployeeCount);

        assertEquals(1, allFour.size());
        assertEquals(Long.valueOf(1), allFour.get(0).monthlyEmployeeCount);
        assertEquals(0, allFour.get(0).monthlyTotal.compareTo(new BigDecimal("4000.00")));
    }

    // ------------------------------------------------------------------
    // E9a–E9d — one projection per partition for each supported GroupKey
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E9a — COUNTRY grouping yields one projection per country")
    void countryGroupingYieldsOneRowPerCountry() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                GroupKey.COUNTRY,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        Map<UUID, AggregateRowProjection> byCountry = result.stream()
                .collect(Collectors.toMap(p -> p.country.id(), Function.identity()));

        assertEquals(3, result.size());
        assertTrue(byCountry.containsKey(DEU));
        assertTrue(byCountry.containsKey(GBR));
        assertTrue(byCountry.containsKey(
                UUID.fromString("00000000-0000-4000-a000-000000000101")));

        AggregateRowProjection deu = byCountry.get(DEU);
        assertEquals(Long.valueOf(4), deu.monthlyEmployeeCount);
        assertEquals(0, deu.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, deu.maxReferenceAmount.compareTo(new BigDecimal("4000.00")));

        AggregateRowProjection gbr = byCountry.get(GBR);
        assertEquals(Long.valueOf(4), gbr.monthlyEmployeeCount);
        assertEquals(0, gbr.minReferenceAmount.compareTo(new BigDecimal("5000.00")));
        assertEquals(0, gbr.maxReferenceAmount.compareTo(new BigDecimal("10000.00")));

        // Other dimensions must stay unset when COUNTRY is the key.
        assertNull(deu.department);
        assertNull(deu.role);
        assertNull(deu.employmentType);
    }

    @Test
    @DisplayName("E9b — DEPARTMENT grouping yields one projection per department")
    void departmentGroupingYieldsOneRowPerDepartment() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                GroupKey.DEPARTMENT,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(4, result.size());
        for (AggregateRowProjection p : result) {
            assertNotNull(p.department);
            assertNull(p.country);
            assertNull(p.role);
            assertNull(p.employmentType);
            assertNotNull(p.minReferenceAmount);
            assertNotNull(p.maxReferenceAmount);
        }
        Map<String, AggregateRowProjection> byCode = result.stream()
                .collect(Collectors.toMap(p -> p.department.code(), Function.identity()));
        assertTrue(byCode.containsKey("ENG"));
        assertTrue(byCode.containsKey("OPS"));
        assertTrue(byCode.containsKey("FIN"));
        assertTrue(byCode.containsKey("HR"));
    }

    @Test
    @DisplayName("E9c — ROLE grouping yields one projection per role")
    void roleGroupingYieldsOneRowPerRole() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                GroupKey.ROLE,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(4, result.size());
        for (AggregateRowProjection p : result) {
            assertNotNull(p.role);
            assertNull(p.country);
            assertNull(p.department);
            assertNull(p.employmentType);
        }
        Map<String, AggregateRowProjection> byCode = result.stream()
                .collect(Collectors.toMap(p -> p.role.code(), Function.identity()));
        assertTrue(byCode.containsKey("SDE"));
        assertTrue(byCode.containsKey("MGR"));
        assertTrue(byCode.containsKey("ANL"));
        assertTrue(byCode.containsKey("OPSR"));
    }

    @Test
    @DisplayName("E9d — EMPLOYMENT_TYPE grouping yields one projection per employment type")
    void employmentTypeGroupingYieldsOneRowPerEmploymentType() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                GroupKey.EMPLOYMENT_TYPE,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(3, result.size());
        for (AggregateRowProjection p : result) {
            assertNotNull(p.employmentType);
            assertNull(p.country);
            assertNull(p.department);
            assertNull(p.role);
        }
        Map<EmploymentType, AggregateRowProjection> byType = result.stream()
                .collect(Collectors.toMap(p -> p.employmentType, Function.identity()));
        assertTrue(byType.containsKey(EmploymentType.FULL_TIME));
        assertTrue(byType.containsKey(EmploymentType.PART_TIME));
        assertTrue(byType.containsKey(EmploymentType.CONTRACTOR));
    }

    // ------------------------------------------------------------------
    // E12 — unsupported group keys
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E12 — CURRENCY and ORIGIN_COUNTRY throw IllegalArgumentException")
    void unsupportedGroupKeysThrow() {
        LocalDate start = LocalDate.parse("2026-01-01");
        LocalDate end = LocalDate.parse("2026-01-31");

        assertThrows(IllegalArgumentException.class, () -> builder.minMax(
                start, end, GroupKey.CURRENCY,
                PayrollPeriodFrequency.MONTHLY, "USD",
                null, null, null, null));

        assertThrows(IllegalArgumentException.class, () -> builder.minMax(
                start, end, GroupKey.ORIGIN_COUNTRY,
                PayrollPeriodFrequency.MONTHLY, "USD",
                null, null, null, null));
    }

    // ------------------------------------------------------------------
    // E1 — partition with only unconvertible summaries is dropped
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E1 — a partition whose only VALIDATED summary is unconvertible is dropped")
    @Sql(scripts = {"/sql/statistics-base.sql", "/sql/statistics-unconvertible.sql"},
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void unconvertibleOnlyPartitionIsDropped() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-08-01"),
                LocalDate.parse("2026-08-31"),
                GroupKey.COUNTRY,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertTrue(result.isEmpty(),
                "XYZ summary must be excluded by the WHERE clause, leaving zero tuples");
    }

    // ------------------------------------------------------------------
    // E13 — dangling country id surfaces as IllegalStateException
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E13 — summary referencing an unknown country id throws IllegalStateException")
    @Sql(scripts = {"/sql/statistics-base.sql", "/sql/statistics-unconvertible.sql"},
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void unknownCountryIdThrowsIllegalStateException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                builder.minMax(
                        LocalDate.parse("2026-09-01"),
                        LocalDate.parse("2026-09-30"),
                        GroupKey.COUNTRY,
                        PayrollPeriodFrequency.MONTHLY,
                        "USD",
                        null, null, null, null));

        assertTrue(ex.getMessage().contains("Country not found"),
                "expected the toProjection country lookup to fail, got: " + ex.getMessage());
    }

    // ------------------------------------------------------------------
    // E3 — mixed-currency partition: reference + convertible + unconvertible
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E3 — mixed-currency partition keeps only the convertible rows")
    void mixedCurrencyPartitionExcludesUnconvertibleRow() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-28"),
                GroupKey.COUNTRY,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(1, result.size(), "only FRA has VALIDATED convertible rows in Feb");
        AggregateRowProjection fra = result.get(0);
        assertEquals(UUID.fromString("00000000-0000-4000-a000-000000000104"), fra.country.id());
        assertEquals(Long.valueOf(2), fra.monthlyEmployeeCount,
                "XYZ (E13) must not count towards employeeCount");
        assertEquals(0, fra.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, fra.maxReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, fra.monthlyTotal.compareTo(new BigDecimal("2000.00")));
    }

    // ------------------------------------------------------------------
    // E14 — mixed partition survives with convertible min/max
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E14 — mixed partition survives with min/max from the convertible rows only")
    void mixedPartitionSurvivesWithConvertibleMinMax() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-02-28"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(1, result.size());
        AggregateRowProjection p = result.get(0);
        assertEquals(Long.valueOf(2), p.monthlyEmployeeCount);
        assertEquals(0, p.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, p.maxReferenceAmount.compareTo(new BigDecimal("1000.00")));
    }

    // ------------------------------------------------------------------
    // E4 — non-reference summary converted by rate
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E4 — EUR summary is converted to USD via the current rate")
    void nonReferenceSummaryConvertedByRate() {
        // Fixture: 2000.00 EUR at 1 USD = 0.50 EUR → 4000.00 USD.
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-03-01"),
                LocalDate.parse("2026-03-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "USD",
                null, null, null, null);

        assertEquals(1, result.size());
        AggregateRowProjection p = result.get(0);
        assertEquals(0, p.minReferenceAmount.compareTo(new BigDecimal("4000.00")));
        assertEquals(0, p.maxReferenceAmount.compareTo(new BigDecimal("4000.00")));
        assertEquals(0, p.monthlyTotal.compareTo(new BigDecimal("4000.00")));
        assertEquals(Long.valueOf(1), p.monthlyEmployeeCount);
    }

    // ------------------------------------------------------------------
    // E16 — reference currency = summary currency → identity conversion
    // ------------------------------------------------------------------

    @Test
    @DisplayName("E16 — GBP reference over GBP summaries performs identity conversion")
    void identityConversionWhenSummaryCurrencyIsReference() {
        List<AggregateRowProjection> result = builder.minMax(
                LocalDate.parse("2026-01-01"),
                LocalDate.parse("2026-01-31"),
                null,
                PayrollPeriodFrequency.MONTHLY,
                "GBP",
                List.of(GBR),
                null, null, null);

        assertEquals(1, result.size());
        AggregateRowProjection p = result.get(0);
        // Only E07 (1000 GBP) and E25 (2000 GBP) survive: USD→GBP has no rate in the fixture.
        assertEquals(Long.valueOf(2), p.monthlyEmployeeCount);
        assertEquals(0, p.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, p.maxReferenceAmount.compareTo(new BigDecimal("2000.00")));
        assertEquals(0, p.monthlyTotal.compareTo(new BigDecimal("3000.00")));
    }
}