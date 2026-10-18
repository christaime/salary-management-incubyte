package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
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

    @Autowired
    private ReportDataRepositoryAdapter adapter;

    @Test
    @DisplayName("queryAggregates groups by country and sums the two groups")
    void queryAggregatesGroupsByCountry() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters);

        assertEquals(2, rows.size());
        for (AggregateRowProjection row : rows) {
            assertNotNull(row.country);
            assertTrue(row.country.code().equals("FR") || row.country.code().equals("DE"));
        }
        AggregateRowProjection fr = rows.stream()
                .filter(r -> "FR".equals(r.country.code())).findFirst().orElseThrow();
        assertEquals("France", fr.country.name());
        assertEquals(0, fr.sumBase.compareTo(new BigDecimal("1080.00")));
        assertEquals(0, fr.sumTotal.compareTo(new BigDecimal("1458.00")));
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
                .filter(r -> "FR".equals(r.country.code())).findFirst().orElseThrow();
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
        assertEquals(0, row.sumTotal.compareTo(new BigDecimal("2187.00")));
        assertEquals(4L, row.sumEmployeeCount);
    }

    @Test
    @DisplayName("queryStatistics returns TOP and BOTTOM rank-1 rows per group")
    void queryStatisticsReturnsTopAndBottom() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters);

        assertEquals(2, rows.size());
        AggregateRowProjection top = rows.stream()
                .filter(r -> EarnerRankingDirection.TOP.equals(r.direction)).findFirst().orElseThrow();
        AggregateRowProjection bottom = rows.stream()
                .filter(r -> EarnerRankingDirection.BOTTOM.equals(r.direction)).findFirst().orElseThrow();
        assertEquals("FR", top.country.code());
        assertEquals(0, top.amount.compareTo(new BigDecimal("9000.00")));
        assertEquals("FR", bottom.country.code());
        assertEquals(0, bottom.amount.compareTo(new BigDecimal("1000.00")));
    }

    @Test
    @DisplayName("queryStatistics applies the country filter")
    void queryStatisticsAppliesCountryFilter() {
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                Set.of(FR), null, null, null);

        List<AggregateRowProjection> rows = adapter.queryStatistics(
                List.of(JANUARY), List.of(GroupKey.COUNTRY), filters);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> "FR".equals(r.country.code())));
    }

    @Test
    @DisplayName("queryAggregates returns empty when no periods match")
    void queryAggregatesEmptyWhenNoPeriods() {
        UUID missing = UUID.fromString("00000000-0000-0000-0000-000000000000");
        ReportEffectiveFilters filters = new ReportEffectiveFilters(
                null, null, null, null);

        List<AggregateRowProjection> rows = adapter.queryAggregates(
                List.of(missing), List.of(GroupKey.COUNTRY), filters);

        assertTrue(rows.isEmpty());
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
}