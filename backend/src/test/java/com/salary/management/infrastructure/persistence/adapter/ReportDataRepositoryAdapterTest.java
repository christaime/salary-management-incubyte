package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.repository.criteria.ReportEffectiveFilters;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link ReportDataRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/report-data-adapter.sql")
class ReportDataRepositoryAdapterTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENGINEERING =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID FINANCE =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID ENGINEER =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID MANAGER =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID JANUARY =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID MISSING =
            UUID.fromString("00000000-0000-0000-0000-000000000000");

    private static final String REFERENCE_CURRENCY = "USD";

    @Autowired
    private ReportDataRepositoryAdapter adapter;

    // ------------------------------------------------------------------
    // queryAggregates
    // ------------------------------------------------------------------

    @Test
    @DisplayName("queryAggregates groups by country and sums the two groups")
    void queryAggregatesGroupsByCountry() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters);

        assertEquals(2, rows.size());
        AggregateRowProjection fr = rows.stream()
                .filter(r -> "FR".equals(r.country.code()))
                .findFirst()
                .orElseThrow();
        assertEquals("France", fr.country.name());
        assertEquals(0, fr.sumBase.compareTo(new BigDecimal("925.93")));
        assertEquals(0, fr.sumTotal.compareTo(new BigDecimal("1250.01")));
        assertEquals(3L, fr.sumEmployeeCount);
    }

    @Test
    @DisplayName("queryAggregates groups by country and department")
    void queryAggregatesGroupsByCountryAndDepartment() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY),
                List.of(GroupKey.COUNTRY, GroupKey.DEPARTMENT),
                filters);

        assertEquals(2, rows.size());
        AggregateRowProjection fr = rows.stream()
                .filter(r -> "FR".equals(r.country.code()))
                .findFirst()
                .orElseThrow();
        assertEquals("Engineering", fr.department.name());
    }

    @Test
    @DisplayName("queryAggregates applies the country filter")
    void queryAggregatesAppliesCountryFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                Set.of(FR), null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters);

        assertEquals(1, rows.size());
        assertEquals("FR", rows.get(0).country.code());
    }

    @Test
    @DisplayName("queryAggregates applies the department filter")
    void queryAggregatesAppliesDepartmentFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, Set.of(FINANCE), null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters);

        assertEquals(1, rows.size());
        assertEquals("DE", rows.get(0).country.code());
    }

    @Test
    @DisplayName("queryAggregates applies the employment type filter")
    void queryAggregatesAppliesEmploymentTypeFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, Set.of(EmploymentType.FULL_TIME));

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters);

        assertEquals(2, rows.size());
    }

    @Test
    @DisplayName("queryAggregates with no group keys returns a single total row")
    void queryAggregatesNoGroupKeysReturnsSingleRow() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY), List.of(), filters);

        assertEquals(1, rows.size());
        AggregateRowProjection row = rows.get(0);
        assertNull(row.country);
        assertNull(row.department);
        assertNull(row.role);
        assertNull(row.employmentType);
        assertNull(row.currency);
        assertEquals(0, row.sumTotal.compareTo(new BigDecimal("1875.01")));
        assertEquals(4L, row.sumEmployeeCount);
    }

    @Test
    @DisplayName("queryAggregates groups by employment type")
    void queryAggregatesGroupsByEmploymentType() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY), List.of(GroupKey.EMPLOYMENT_TYPE), filters);

        assertEquals(1, rows.size());
        assertEquals(EmploymentType.FULL_TIME, rows.get(0).employmentType);
    }

    @Test
    @DisplayName("queryAggregates returns empty when no periods match")
    void queryAggregatesEmptyWhenNoPeriods() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(MISSING), List.of(GroupKey.COUNTRY), filters);

        assertTrue(rows.isEmpty());
    }

    // ------------------------------------------------------------------
    // queryStatistics
    // ------------------------------------------------------------------

    @Test
    @DisplayName("queryStatistics by COUNTRY returns one row per country")
    void queryStatisticsByCountry() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters, REFERENCE_CURRENCY);

        assertEquals(2, rows.size());
        AggregateRowProjection fr = rows.stream()
                .filter(r -> "FR".equals(r.country.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, fr.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, fr.maxReferenceAmount.compareTo(new BigDecimal("2500.00")));

        AggregateRowProjection de = rows.stream()
                .filter(r -> "DE".equals(r.country.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, de.minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertEquals(0, de.maxReferenceAmount.compareTo(new BigDecimal("625.00")));
    }

    @Test
    @DisplayName("queryStatistics by COUNTRY applies the country filter")
    void queryStatisticsByCountryAppliesCountryFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                Set.of(FR), null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters, REFERENCE_CURRENCY);

        assertEquals(1, rows.size());
        assertEquals("FR", rows.get(0).country.code());
        assertEquals(0, rows.get(0).minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, rows.get(0).maxReferenceAmount.compareTo(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("queryStatistics by DEPARTMENT returns one row per department")
    void queryStatisticsByDepartment() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.DEPARTMENT), filters, REFERENCE_CURRENCY);

        assertEquals(2, rows.size());
        AggregateRowProjection eng = rows.stream()
                .filter(r -> r.department != null && "Engineering".equals(r.department.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, eng.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, eng.maxReferenceAmount.compareTo(new BigDecimal("2500.00")));

        AggregateRowProjection fin = rows.stream()
                .filter(r -> r.department != null && "Finance".equals(r.department.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, fin.minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertEquals(0, fin.maxReferenceAmount.compareTo(new BigDecimal("625.00")));
    }

    @Test
    @DisplayName("queryStatistics by DEPARTMENT applies the department filter")
    void queryStatisticsByDepartmentAppliesDepartmentFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, Set.of(FINANCE), null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.DEPARTMENT), filters,
                REFERENCE_CURRENCY);

        assertEquals(1, rows.size());
        assertEquals("Finance", rows.get(0).department.name());
    }

    @Test
    @DisplayName("queryStatistics by ROLE returns one row per role")
    void queryStatisticsByRole() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.ROLE), filters, REFERENCE_CURRENCY);

        assertEquals(2, rows.size());
        AggregateRowProjection eng = rows.stream()
                .filter(r -> r.role != null && "Engineer".equals(r.role.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, eng.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, eng.maxReferenceAmount.compareTo(new BigDecimal("2500.00")));

        AggregateRowProjection mgr = rows.stream()
                .filter(r -> r.role != null && "Manager".equals(r.role.name()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, mgr.minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertEquals(0, mgr.maxReferenceAmount.compareTo(new BigDecimal("625.00")));
    }

    @Test
    @DisplayName("queryStatistics by ROLE applies the role filter")
    void queryStatisticsByRoleAppliesRoleFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, Set.of(MANAGER), null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.ROLE), filters, REFERENCE_CURRENCY);

        assertEquals(1, rows.size());
        assertEquals("Manager", rows.get(0).role.name());
    }

    @Test
    @DisplayName("queryStatistics by EMPLOYMENT_TYPE returns one row per type")
    void queryStatisticsByEmploymentType() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.EMPLOYMENT_TYPE), filters,
                REFERENCE_CURRENCY);

        assertEquals(1, rows.size());
        AggregateRowProjection row = rows.get(0);
        assertEquals(EmploymentType.FULL_TIME, row.employmentType);
        assertEquals(0, row.minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertEquals(0, row.maxReferenceAmount.compareTo(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("queryStatistics by EMPLOYMENT_TYPE applies the type filter")
    void queryStatisticsByEmploymentTypeAppliesFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, Set.of(EmploymentType.FULL_TIME));

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.EMPLOYMENT_TYPE), filters,
                REFERENCE_CURRENCY);

        assertEquals(1, rows.size());
        assertEquals(EmploymentType.FULL_TIME, rows.get(0).employmentType);
    }

    @Test
    @DisplayName("queryStatistics with no group keys returns the global row")
    void queryStatisticsGlobal() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(), filters, REFERENCE_CURRENCY);

        assertEquals(1, rows.size());
        AggregateRowProjection row = rows.get(0);
        assertNull(row.country);
        assertNull(row.department);
        assertNull(row.role);
        assertNull(row.employmentType);
        assertEquals(0, row.minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertEquals(0, row.maxReferenceAmount.compareTo(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("queryStatistics with more than one group key returns empty")
    void queryStatisticsWithMultipleGroupKeysReturnsEmpty() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY),
                List.of(GroupKey.COUNTRY, GroupKey.DEPARTMENT),
                filters, REFERENCE_CURRENCY);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("queryStatistics returns empty when no periods match")
    void queryStatisticsEmptyWhenNoPeriods() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(MISSING), List.of(GroupKey.COUNTRY), filters, REFERENCE_CURRENCY);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("queryStatistics has no statistics for a missing group")
    void queryStatisticsMissingGroupIsNotPresent() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                Set.of(UUID.fromString("99999999-9999-9999-9999-999999999999")),
                null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters, REFERENCE_CURRENCY);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("queryStatistics values are scaled to the amount scale")
    void queryStatisticsValuesAreScaled() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters, REFERENCE_CURRENCY);

        assertNotNull(rows);
        for (AggregateRowProjection row : rows) {
            if (row.minReferenceAmount != null) {
                assertEquals(2, row.minReferenceAmount.scale());
            }
            if (row.maxReferenceAmount != null) {
                assertEquals(2, row.maxReferenceAmount.scale());
            }
        }
    }
}