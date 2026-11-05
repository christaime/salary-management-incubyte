package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerPercentileEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import com.salary.management.infrastructure.persistence.repository.DynamicRankingBuilder.RankingAndPercentileResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link DynamicRankingBuilder}.
 *
 * <p>Scope: {@code rankAndComputePercentilesForPeriod(...)} — TOP/BOTTOM rankings
 * and 7 percentile rows per qualifying partition.</p>
 *
 * <p>Fixtures:</p>
 * <ul>
 *   <li>{@code /sql/ranking-base.sql} (class-level default) — ten periods covering
 *       P1 baseline, P2 small LUX partition, P2b/P3/P4a-c mixed-frequency and
 *       bi-weekly days-covered cases, P5 empty.</li>
 *   <li>{@code /sql/ranking-small.sql} (method-level override for R4 and R10) —
 *       S1 (n=9, percentiles suppressed) and S2 (BI_WEEKLY period).</li>
 * </ul>
 *
 * <p>Monetary comparisons use {@code assertAmountClose} with a one-cent tolerance.
 * SQLite stores {@code NUMERIC} as {@code REAL}, so the annualised percentile value
 * of e.g. {@code 600 / 31 × 31 × 12} can come back as {@code 7199.99…} instead of
 * exactly {@code 7200.00}; an exact {@code compareTo} would be brittle without
 * strengthening the assertion meaningfully (wrong rows differ by whole units).</p>
 *
 * <p><b>R17 is intentionally not implemented.</b> The original plan called for a test
 * where a summary row has a null {@code assignment_country_id} and the builder throws
 * {@link IllegalStateException}. That case cannot be expressed with the current schema:
 * {@code payroll_summaries.assignment_country_id} is {@code NOT NULL} in
 * {@code V1__initial_schema.sql}, so the null check in {@code toRankingEntity} is
 * unreachable through the public API. Documented as a known coverage gap.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/ranking-base.sql")
class DynamicRankingBuilderTest {

    private static final UUID FRA = UUID.fromString("00000000-0000-4000-a000-000000000104");
    private static final UUID DEU = UUID.fromString("00000000-0000-4000-a000-000000000102");
    private static final UUID LUX = UUID.fromString("00000000-0000-4000-a000-00000000010c");

    private static final UUID E01 = UUID.fromString("00000000-0000-4000-a000-000000000401");
    private static final UUID E02 = UUID.fromString("00000000-0000-4000-a000-000000000402");
    private static final UUID E03 = UUID.fromString("00000000-0000-4000-a000-000000000403");
    private static final UUID E04 = UUID.fromString("00000000-0000-4000-a000-000000000404");
    private static final UUID E05 = UUID.fromString("00000000-0000-4000-a000-000000000405");
    private static final UUID E06 = UUID.fromString("00000000-0000-4000-a000-000000000406");
    private static final UUID E07 = UUID.fromString("00000000-0000-4000-a000-000000000407");
    private static final UUID E08 = UUID.fromString("00000000-0000-4000-a000-000000000408");
    private static final UUID E09 = UUID.fromString("00000000-0000-4000-a000-000000000409");
    private static final UUID E10 = UUID.fromString("00000000-0000-4000-a000-00000000040a");
    private static final UUID E11 = UUID.fromString("00000000-0000-4000-a000-00000000040b");
    private static final UUID E12 = UUID.fromString("00000000-0000-4000-a000-00000000040c");
    private static final UUID E13 = UUID.fromString("00000000-0000-4000-a000-00000000040d");
    private static final UUID E14 = UUID.fromString("00000000-0000-4000-a000-00000000040e");
    private static final UUID E15 = UUID.fromString("00000000-0000-4000-a000-00000000040f");
    private static final UUID E20 = UUID.fromString("00000000-0000-4000-a000-000000000414");

    private static final UUID P1 = UUID.fromString("00000000-0000-4000-a000-000000000601");
    private static final UUID P2 = UUID.fromString("00000000-0000-4000-a000-000000000602");
    private static final UUID P3 = UUID.fromString("00000000-0000-4000-a000-000000000603");
    private static final UUID P4 = UUID.fromString("00000000-0000-4000-a000-000000000604");
    private static final UUID P5 = UUID.fromString("00000000-0000-4000-a000-000000000605");

    @Autowired
    private DynamicRankingBuilder builder;

    @Autowired
    private EntityManager entityManager;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PayrollPeriodEntity period(UUID id) {
        PayrollPeriodEntity p = entityManager.find(PayrollPeriodEntity.class, id);
        assertNotNull(p, "fixture is missing payroll period " + id);
        return p;
    }

    private static List<PayrollPeriodEarnerRankingEntity> only(
            List<PayrollPeriodEarnerRankingEntity> rankings,
            EarnerRankingDirection direction) {
        return rankings.stream().filter(r -> r.getDirection() == direction).toList();
    }

    private static PayrollPeriodEarnerRankingEntity byEmployee(
            RankingAndPercentileResult result, UUID employeeId) {
        return result.rankings().stream()
                .filter(r -> employeeId.equals(r.getEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no ranking for " + employeeId));
    }

    /**
     * Compares a monetary value with a one-cent tolerance. Any failure message
     * carries the actual value, so a wrong-row problem is visible immediately.
     */
    private static void assertAmountClose(String expected, BigDecimal actual) {
        assertNotNull(actual, "amount is null, expected ~" + expected);
        BigDecimal exp = new BigDecimal(expected);
        BigDecimal delta = actual.subtract(exp).abs();
        assertTrue(delta.compareTo(new BigDecimal("0.01")) <= 0,
                "expected ~" + expected + " but was " + actual
                        + " (delta " + delta + ")");
    }

    // ------------------------------------------------------------------
    // R1 — empty period → empty rankings, empty percentiles
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R1 — empty period yields no rankings and no percentiles")
    void emptyPeriodYieldsNoRankingsAndNoPercentiles() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P5), "USD", null, 5);

        assertTrue(result.rankings().isEmpty());
        assertTrue(result.percentiles().isEmpty());
    }

    // ------------------------------------------------------------------
    // R2 — small partition (n=3) → TOP and BOTTOM both list all 3
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R2 — small partition lists the same employees on both sides (overlap intended)")
    void smallPartitionListsSameEmployeesOnBothSides() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P2), "USD", GroupKey.COUNTRY, 5);

        List<PayrollPeriodEarnerRankingEntity> lux = result.rankings().stream()
                .filter(r -> LUX.equals(r.getCountryId()))
                .toList();

        List<UUID> luxTop = only(lux, EarnerRankingDirection.TOP).stream()
                .map(PayrollPeriodEarnerRankingEntity::getEmployeeId).toList();
        List<UUID> luxBottom = only(lux, EarnerRankingDirection.BOTTOM).stream()
                .map(PayrollPeriodEarnerRankingEntity::getEmployeeId).toList();

        assertEquals(3, luxTop.size());
        assertEquals(3, luxBottom.size());
        assertEquals(Set.of(E05, E06, E07), new HashSet<>(luxTop));
        assertEquals(Set.of(E05, E06, E07), new HashSet<>(luxBottom));
    }

    // ------------------------------------------------------------------
    // R3 — 7 percentile rows per qualifying partition, employeeCount = n
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R3 — 10+ employees per partition yields 7 percentile rows with employeeCount = n")
    void qualifyingPartitionsEmitSevenPercentileRows() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.COUNTRY, 5);

        List<PayrollPeriodEarnerPercentileEntity> fra = result.percentiles().stream()
                .filter(p -> FRA.equals(p.getCountryId()))
                .toList();
        List<PayrollPeriodEarnerPercentileEntity> deu = result.percentiles().stream()
                .filter(p -> DEU.equals(p.getCountryId()))
                .toList();

        assertEquals(7, fra.size());
        assertEquals(7, deu.size());
        for (PayrollPeriodEarnerPercentileEntity p : fra) {
            assertEquals(10, p.getEmployeeCount());
        }
        for (PayrollPeriodEarnerPercentileEntity p : deu) {
            assertEquals(10, p.getEmployeeCount());
        }

        Set<Integer> expected = Set.of(0, 10, 25, 50, 75, 90, 100);
        assertEquals(expected, new HashSet<>(fra.stream()
                .map(PayrollPeriodEarnerPercentileEntity::getPercentile).toList()));
    }

    // ------------------------------------------------------------------
    // R4 — n = 9 → percentiles suppressed, rankings still emitted (small fixture)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R4 — n = 9 suppresses percentiles but still emits rankings")
    @Sql(scripts = "/sql/ranking-small.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void nineEmployeesSuppressPercentilesKeepRankings() {
        PayrollPeriodEntity s1 = period(
                UUID.fromString("00000000-0000-4000-a000-000000000601"));

        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                s1, "USD", GroupKey.COUNTRY, 5);

        assertFalse(result.rankings().isEmpty());

        List<PayrollPeriodEarnerRankingEntity> top =
                only(result.rankings(), EarnerRankingDirection.TOP);
        List<PayrollPeriodEarnerRankingEntity> bottom =
                only(result.rankings(), EarnerRankingDirection.BOTTOM);

        assertEquals(5, top.size());
        assertEquals(5, bottom.size());

        // S1 monthlyRate ascending: 100 200 300 400 500 600 700 800 900 (E09..E01).
        // TOP-5 desc: 900 800 700 600 500 -> E01..E05.
        // BOTTOM-5 asc: 100 200 300 400 500 -> E09 E08 E07 E06 E05.
        assertEquals(Set.of(
                        UUID.fromString("00000000-0000-4000-a000-000000000401"),
                        UUID.fromString("00000000-0000-4000-a000-000000000402"),
                        UUID.fromString("00000000-0000-4000-a000-000000000403"),
                        UUID.fromString("00000000-0000-4000-a000-000000000404"),
                        UUID.fromString("00000000-0000-4000-a000-000000000405")),
                new HashSet<>(top.stream()
                        .map(PayrollPeriodEarnerRankingEntity::getEmployeeId).toList()));

        assertEquals(Set.of(
                        UUID.fromString("00000000-0000-4000-a000-000000000409"),
                        UUID.fromString("00000000-0000-4000-a000-000000000408"),
                        UUID.fromString("00000000-0000-4000-a000-000000000407"),
                        UUID.fromString("00000000-0000-4000-a000-000000000406"),
                        UUID.fromString("00000000-0000-4000-a000-000000000405")),
                new HashSet<>(bottom.stream()
                        .map(PayrollPeriodEarnerRankingEntity::getEmployeeId).toList()));

        assertTrue(result.percentiles().isEmpty());
    }

    // ------------------------------------------------------------------
    // R5 — percentile markers map correctly (CEIL(n*p/100), clamped to >= 1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R5 — percentile markers follow CEIL(n*p/100) clamped to >= 1")
    void percentileMarkersFollowCeilClamped() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.COUNTRY, 5);

        // FRA n=10, ascending monthlyRate: 600 700 800 900 1000 1200 1400 1600 1800 2000
        // 1-based rank = CEIL(n * p / 100), clamped to >= 1:
        //   P0   -> CEIL(0)   = 0  -> clamp 1  -> 600  -> 600*12  = 7200
        //   P10  -> CEIL(1)   = 1  -> 600       -> 7200
        //   P25  -> CEIL(2.5) = 3  -> 800       -> 9600
        //   P50  -> CEIL(5.0) = 5  -> 1000      -> 12000
        //   P75  -> CEIL(7.5) = 8  -> 1600      -> 19200
        //   P90  -> CEIL(9.0) = 9  -> 1800      -> 21600
        //   P100 -> CEIL(10)  = 10 -> 2000      -> 24000
        List<PayrollPeriodEarnerPercentileEntity> fra = result.percentiles().stream()
                .filter(p -> FRA.equals(p.getCountryId()))
                .toList();

        assertAmountClose("7200.00",  fraPercentile(fra, 0));
        assertAmountClose("7200.00",  fraPercentile(fra, 10));
        assertAmountClose("9600.00",  fraPercentile(fra, 25));
        assertAmountClose("12000.00", fraPercentile(fra, 50));
        assertAmountClose("19200.00", fraPercentile(fra, 75));
        assertAmountClose("21600.00", fraPercentile(fra, 90));
        assertAmountClose("24000.00", fraPercentile(fra, 100));
    }

    private static BigDecimal fraPercentile(
            List<PayrollPeriodEarnerPercentileEntity> rows, int percentile) {
        return rows.stream()
                .filter(r -> r.getPercentile() == percentile)
                .map(PayrollPeriodEarnerPercentileEntity::getReferenceAmount)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no percentile " + percentile));
    }

    // ------------------------------------------------------------------
    // R6 — percentiles only from partitions with n >= 10; rankings from every partition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R6 — percentiles only from partitions with n >= 10; rankings from every partition")
    void percentilesOnlyFromLargePartitions() {
        // P2, COUNTRY grouping: FRA (n=4) and LUX (n=3) — neither qualifies.
        RankingAndPercentileResult small = builder.rankAndComputePercentilesForPeriod(
                period(P2), "USD", GroupKey.COUNTRY, 5);

        List<UUID> fraTop = small.rankings().stream()
                .filter(r -> FRA.equals(r.getCountryId()))
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP)
                .map(PayrollPeriodEarnerRankingEntity::getEmployeeId)
                .toList();
        List<UUID> luxTop = small.rankings().stream()
                .filter(r -> LUX.equals(r.getCountryId()))
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP)
                .map(PayrollPeriodEarnerRankingEntity::getEmployeeId)
                .toList();

        assertEquals(4, fraTop.size());
        assertEquals(3, luxTop.size());
        assertTrue(small.percentiles().isEmpty());

        // P1, COUNTRY grouping: FRA (n=10) and DEU (n=10) — both qualify, 7 rows each.
        RankingAndPercentileResult large = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.COUNTRY, 5);
        assertEquals(14, large.percentiles().size());
    }

    // ------------------------------------------------------------------
    // R7 — daysCovered per employee = sum of their bi-weekly days in range
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R7 — daysCovered sums bi-weekly contributions per employee (April)")
    void daysCoveredSumsBiWeeklyContributionsPerEmployee() {
        // P4 range covers P4a/P4b/P4c.
        //   E01: 1 BI_WEEKLY (1400)         -> days 14, monthlyRate = 1400/14*30 = 3000.00
        //   E02: 2 BI_WEEKLY (1200 + 1200)  -> days 28, monthlyRate = 2400/28*30 = 2571.43
        //   E03: 3 BI_WEEKLY (1000*3)       -> days 42, monthlyRate = 3000/42*30 = 2142.86
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P4), "USD", null, 10);

        PayrollPeriodEarnerRankingEntity e01 = byEmployee(result, E01);
        PayrollPeriodEarnerRankingEntity e02 = byEmployee(result, E02);
        PayrollPeriodEarnerRankingEntity e03 = byEmployee(result, E03);

        assertEquals(14, e01.getDaysCovered());
        assertAmountClose("3000.00", e01.getMonthlyRateAmount().getAmount());

        assertEquals(28, e02.getDaysCovered());
        assertAmountClose("2571.43", e02.getMonthlyRateAmount().getAmount());

        assertEquals(42, e03.getDaysCovered());
        assertAmountClose("2142.86", e03.getMonthlyRateAmount().getAmount());
    }

    // ------------------------------------------------------------------
    // R8 — MONTHLY period, MONTHLY-only summaries → monthlyRate = refTotal
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R8 — MONTHLY-only summaries yield monthlyRate equal to referenceAmount")
    void monthlyOnlySummariesYieldMonthlyRateEqualToReference() {
        // Use GroupKey.COUNTRY so E01 surfaces in the FRA TOP window (limit=5).
        // Under GroupKey=null with 20 employees and limit=5, E01 (2000) sits at
        // rank 8 desc / 13 asc — outside both windows.
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.COUNTRY, 5);

        PayrollPeriodEarnerRankingEntity e01 = result.rankings().stream()
                .filter(r -> FRA.equals(r.getCountryId()))
                .filter(r -> E01.equals(r.getEmployeeId()))
                .findFirst().orElseThrow();

        // E01 has a single MONTHLY summary of 2000.00 USD in P1, 31-day month.
        assertAmountClose("2000.00", e01.getReferenceAmount().getAmount());
        assertEquals("USD", e01.getReferenceAmount().getCurrency());

        assertEquals(31, e01.getDaysCovered());
        assertAmountClose("2000.00", e01.getMonthlyRateAmount().getAmount());
        assertEquals("USD", e01.getMonthlyRateAmount().getCurrency());
    }

    // ------------------------------------------------------------------
    // R9 — MONTHLY period with BI_WEEKLY summaries in range
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R9 — mixed frequencies in Feb produce monthlyRate = SUM(ref) / SUM(days) * 28")
    void mixedFrequenciesInFebUseWeightedMonthlyRate() {
        // P2 range covers P2 (MONTHLY) and P2b (BI_WEEKLY), both start_date in Feb.
        //   E01: 1200 (28 d) + 1400 (14 d) = 2600 / 42 * 28 = 1733.33
        //   E02: 1000 (28 d) + 1200 (14 d) = 2200 / 42 * 28 = 1466.67
        //   E03: 800 (28 d)                 = 800 / 28 * 28 = 800.00
        //   E04: 600 (28 d)                 = 600 / 28 * 28 = 600.00
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P2), "USD", null, 10);

        PayrollPeriodEarnerRankingEntity e01 = byEmployee(result, E01);
        PayrollPeriodEarnerRankingEntity e02 = byEmployee(result, E02);
        PayrollPeriodEarnerRankingEntity e03 = byEmployee(result, E03);
        PayrollPeriodEarnerRankingEntity e04 = byEmployee(result, E04);

        assertEquals(42, e01.getDaysCovered());
        assertAmountClose("1733.33", e01.getMonthlyRateAmount().getAmount());

        assertEquals(42, e02.getDaysCovered());
        assertAmountClose("1466.67", e02.getMonthlyRateAmount().getAmount());

        assertEquals(28, e03.getDaysCovered());
        assertAmountClose("800.00", e03.getMonthlyRateAmount().getAmount());

        assertEquals(28, e04.getDaysCovered());
        assertAmountClose("600.00", e04.getMonthlyRateAmount().getAmount());
    }

    // ------------------------------------------------------------------
    // R10 — BI_WEEKLY period, monthlyRate = ref / 14 * daysInMonth (small fixture)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R10 — BI_WEEKLY period computes monthlyRate = ref / 14 * daysInMonth")
    @Sql(scripts = "/sql/ranking-small.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void biWeeklyPeriodComputesMonthlyRateFromDaysInMonth() {
        // S2: July 2026, BI_WEEKLY. One summary: E01, 1400.00 USD.
        // daysCovered = 14, daysInMonth = 31 -> monthlyRate = 1400 / 14 * 31 = 3100.00.
        PayrollPeriodEntity s2 = period(
                UUID.fromString("00000000-0000-4000-a000-000000000602"));

        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                s2, "USD", null, 5);

        assertFalse(result.rankings().isEmpty());
        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertEquals(
                    UUID.fromString("00000000-0000-4000-a000-000000000401"),
                    r.getEmployeeId());
            assertEquals(14, r.getDaysCovered());
            assertAmountClose("3100.00", r.getMonthlyRateAmount().getAmount());
            assertEquals("USD", r.getMonthlyRateAmount().getCurrency());
        }

        assertTrue(result.percentiles().isEmpty());
    }

    // ------------------------------------------------------------------
    // R11 — ties broken by employee_id ASC
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R11 — identical monthlyRate is ordered by employee_id ASC")
    void identicalMonthlyRateOrderedByEmployeeId() {
        // P3: E01 (…401) = E02 (…402) = 5000.00.
        RankingAndPercentileResult one = builder.rankAndComputePercentilesForPeriod(
                period(P3), "USD", null, 1);

        List<PayrollPeriodEarnerRankingEntity> oneTop =
                only(one.rankings(), EarnerRankingDirection.TOP);
        assertEquals(1, oneTop.size());
        assertEquals(E01, oneTop.get(0).getEmployeeId());

        RankingAndPercentileResult two = builder.rankAndComputePercentilesForPeriod(
                period(P3), "USD", null, 2);

        List<PayrollPeriodEarnerRankingEntity> twoTop =
                only(two.rankings(), EarnerRankingDirection.TOP);
        assertEquals(2, twoTop.size());
        assertEquals(E01, twoTop.get(0).getEmployeeId());
        assertEquals(E02, twoTop.get(1).getEmployeeId());
    }

    // ------------------------------------------------------------------
    // R12 — TOP and BOTTOM each capped at limit = 5
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R12 — TOP and BOTTOM are each capped at the requested limit")
    void topAndBottomRespectLimit() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", null, 5);

        List<PayrollPeriodEarnerRankingEntity> top =
                only(result.rankings(), EarnerRankingDirection.TOP);
        List<PayrollPeriodEarnerRankingEntity> bottom =
                only(result.rankings(), EarnerRankingDirection.BOTTOM);

        assertEquals(5, top.size());
        assertEquals(5, bottom.size());

        // P1 global ascending: 500 600 700 800 900 1000 1000 1200 1400 1500 1600 …
        // TOP-5 desc: E11 (5000) E12 (4500) E13 (4000) E14 (3500) E15 (3000)
        // BOTTOM-5 asc: E20 (500) E10 (600) E09 (700) E08 (800) E07 (900)
        assertEquals(Set.of(E11, E12, E13, E14, E15),
                new HashSet<>(top.stream()
                        .map(PayrollPeriodEarnerRankingEntity::getEmployeeId).toList()));
        assertEquals(Set.of(E20, E10, E09, E08, E07),
                new HashSet<>(bottom.stream()
                        .map(PayrollPeriodEarnerRankingEntity::getEmployeeId).toList()));
    }

    // ------------------------------------------------------------------
    // R13 — GroupKey = null → single group with null dimension columns
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R13 — GroupKey = null emits rows with all four dimension columns null")
    void nullGroupKeyEmitsNullDimensions() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", null, 5);

        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertNull(r.getCountryId());
            assertNull(r.getDepartmentId());
            assertNull(r.getRoleId());
            assertNull(r.getEmploymentType());
        }
        for (PayrollPeriodEarnerPercentileEntity p : result.percentiles()) {
            assertNull(p.getCountryId());
            assertNull(p.getDepartmentId());
            assertNull(p.getRoleId());
            assertNull(p.getEmploymentType());
        }
    }

    // ------------------------------------------------------------------
    // R14a — COUNTRY partition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R14a — COUNTRY grouping sets countryId and leaves the other three null")
    void countryGroupingSetsCountryIdOnly() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.COUNTRY, 5);

        assertFalse(result.rankings().isEmpty());
        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertNotNull(r.getCountryId());
            assertTrue(FRA.equals(r.getCountryId()) || DEU.equals(r.getCountryId()));
            assertNull(r.getDepartmentId());
            assertNull(r.getRoleId());
            assertNull(r.getEmploymentType());
        }
        for (PayrollPeriodEarnerPercentileEntity p : result.percentiles()) {
            assertNotNull(p.getCountryId());
            assertNull(p.getDepartmentId());
            assertNull(p.getRoleId());
            assertNull(p.getEmploymentType());
        }
    }

    // ------------------------------------------------------------------
    // R14b — DEPARTMENT partition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R14b — DEPARTMENT grouping sets departmentId and leaves the other three null")
    void departmentGroupingSetsDepartmentIdOnly() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.DEPARTMENT, 5);

        UUID eng = UUID.fromString("00000000-0000-4000-a000-000000000201");
        UUID ops = UUID.fromString("00000000-0000-4000-a000-000000000202");

        assertFalse(result.rankings().isEmpty());
        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertNotNull(r.getDepartmentId());
            assertTrue(eng.equals(r.getDepartmentId()) || ops.equals(r.getDepartmentId()));
            assertNull(r.getCountryId());
            assertNull(r.getRoleId());
            assertNull(r.getEmploymentType());
        }
        for (PayrollPeriodEarnerPercentileEntity p : result.percentiles()) {
            assertNotNull(p.getDepartmentId());
            assertNull(p.getCountryId());
            assertNull(p.getRoleId());
            assertNull(p.getEmploymentType());
        }
    }

    // ------------------------------------------------------------------
    // R14c — ROLE partition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R14c — ROLE grouping sets roleId and leaves the other three null")
    void roleGroupingSetsRoleIdOnly() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.ROLE, 5);

        UUID sde = UUID.fromString("00000000-0000-4000-a000-000000000301");
        UUID mgr = UUID.fromString("00000000-0000-4000-a000-000000000302");

        assertFalse(result.rankings().isEmpty());
        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertNotNull(r.getRoleId());
            assertTrue(sde.equals(r.getRoleId()) || mgr.equals(r.getRoleId()));
            assertNull(r.getCountryId());
            assertNull(r.getDepartmentId());
            assertNull(r.getEmploymentType());
        }
        for (PayrollPeriodEarnerPercentileEntity p : result.percentiles()) {
            assertNotNull(p.getRoleId());
            assertNull(p.getCountryId());
            assertNull(p.getDepartmentId());
            assertNull(p.getEmploymentType());
        }
    }

    // ------------------------------------------------------------------
    // R14d — EMPLOYMENT_TYPE partition
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R14d — EMPLOYMENT_TYPE grouping sets employmentType and leaves the other three null")
    void employmentTypeGroupingSetsEmploymentTypeOnly() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.EMPLOYMENT_TYPE, 5);

        assertFalse(result.rankings().isEmpty());
        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertEquals(EmploymentType.FULL_TIME, r.getEmploymentType());
            assertNull(r.getCountryId());
            assertNull(r.getDepartmentId());
            assertNull(r.getRoleId());
        }
        for (PayrollPeriodEarnerPercentileEntity p : result.percentiles()) {
            assertEquals("FULL_TIME", p.getEmploymentType());
            assertNull(p.getCountryId());
            assertNull(p.getDepartmentId());
            assertNull(p.getRoleId());
        }
    }

    // ------------------------------------------------------------------
    // R14e / R14f — unsupported group keys throw
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R14e/f — CURRENCY and ORIGIN_COUNTRY throw IllegalArgumentException")
    void unsupportedGroupKeysThrow() {
        PayrollPeriodEntity p1 = period(P1);
        assertThrows(IllegalArgumentException.class,
                () -> builder.rankAndComputePercentilesForPeriod(
                        p1, "USD", GroupKey.CURRENCY, 5));
        assertThrows(IllegalArgumentException.class,
                () -> builder.rankAndComputePercentilesForPeriod(
                        p1, "USD", GroupKey.ORIGIN_COUNTRY, 5));
    }

    // ------------------------------------------------------------------
    // R15 — every emitted row has status = DRAFT
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R15 — every emitted ranking and percentile has status = DRAFT")
    void everyEmittedRowIsDraft() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", null, 5);

        assertFalse(result.rankings().isEmpty());
        assertFalse(result.percentiles().isEmpty());
        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertEquals(PayrollSummaryStatus.DRAFT, r.getStatus());
        }
        for (PayrollPeriodEarnerPercentileEntity p : result.percentiles()) {
            assertEquals(PayrollSummaryStatus.DRAFT, p.getStatus());
        }
    }

    // ------------------------------------------------------------------
    // R16 — every emitted row has a unique id
    // ------------------------------------------------------------------

    @Test
    @DisplayName("R16 — every emitted ranking and percentile has a unique id")
    void everyEmittedRowHasUniqueId() {
        RankingAndPercentileResult result = builder.rankAndComputePercentilesForPeriod(
                period(P1), "USD", GroupKey.COUNTRY, 5);

        Set<UUID> ids = new HashSet<>();
        for (PayrollPeriodEarnerRankingEntity r : result.rankings()) {
            assertTrue(ids.add(r.getId()),
                    "duplicate ranking id: " + r.getId());
        }
        for (PayrollPeriodEarnerPercentileEntity p : result.percentiles()) {
            assertTrue(ids.add(p.getId()),
                    "duplicate percentile id: " + p.getId());
        }
    }
}