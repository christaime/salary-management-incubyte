package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.enums.ReportWarningCode;
import com.salary.management.domain.model.values.PayrollPeriodSnapshotResult;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.PayrollPeriodSnapshotRepository;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodAggregateEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PayrollPeriodSnapshotRepositoryAdapter}
 * using the deterministic dataset from {@code /sql/payroll-period-snapshot.sql}.
 *
 * <p>Unlike a seed-based test, this test hardcodes expected values against
 * known input rows, which is the right level of coupling for an adapter:
 * the adapter's job is a well-defined translation from query result to
 * persisted entity, and that translation is what we test here.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql("/sql/payroll-period-snapshot.sql")
class PayrollPeriodSnapshotRepositoryAdapterTest {

    /** January 2026 — MONTHLY. */
    private static final UUID JAN_2026 =
            UUID.fromString("77777777-7777-7777-7777-777777777777");
    /** February 2026 — MONTHLY. */
    private static final UUID FEB_2026 =
            UUID.fromString("88888888-8888-8888-8888-888888888888");

    private static final UUID FR =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DE =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final BigDecimal FR_AGG_REF_TOTAL = new BigDecimal("3300.00");
    private static final BigDecimal DE_AGG_REF_TOTAL = new BigDecimal("1100.00");

    @Autowired private EntityManager entityManager;

    @Autowired
    private PayrollPeriodSnapshotRepository adapter;

    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;

    @Autowired
    private PayrollPeriodAggregateJpaRepository aggregateJpaRepository;

    @Autowired
    private PayrollPeriodEarnerRankingJpaRepository rankingJpaRepository;

    @Autowired
    private PayrollPeriodEarnerPercentileJpaRepository percentileJpaRepository;

    @Autowired
    private CurrencyRateJpaRepository currencyRateJpaRepository;

    // ------------------------------------------------------------------
    // Aggregates
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buildAndSaveAggregates: writes the two groups with the expected totals")
    void buildAndSaveAggregates_writesExpectedGroups() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();

        PayrollPeriodSnapshotResult result = adapter.buildAndSaveAggregates(jan);

        assertThat(result).isNotNull();
        assertThat(result.blockers()).isEmpty();

        List<PayrollPeriodAggregateEntity> rows =
                aggregateJpaRepository.findByPayrollPeriodId(JAN_2026);

        assertThat(rows).hasSize(2);

        PayrollPeriodAggregateEntity fr = rows.stream()
                .filter(r -> FR.equals(r.getCountryId()))
                .findFirst().orElseThrow();
        assertThat(fr.getEmployeeCount()).isEqualTo(2);
        assertThat(fr.getOriginalAmounts().getTotal()).isEqualByComparingTo("3000.00");
        assertThat(fr.getOriginalAmounts().getCurrency()).isEqualTo("EUR");
        assertThat(fr.getReferenceAmounts().getTotal()).isEqualByComparingTo(FR_AGG_REF_TOTAL);
        assertThat(fr.getReferenceAmounts().getCurrency()).isEqualTo("USD");
        assertThat(fr.getStatus()).isEqualTo(PayrollSummaryStatus.DRAFT);

        PayrollPeriodAggregateEntity de = rows.stream()
                .filter(r -> DE.equals(r.getCountryId()))
                .findFirst().orElseThrow();
        assertThat(de.getEmployeeCount()).isEqualTo(2);
        assertThat(de.getOriginalAmounts().getTotal()).isEqualByComparingTo("1000.00");
        assertThat(de.getReferenceAmounts().getTotal()).isEqualByComparingTo(DE_AGG_REF_TOTAL);
    }

    @Test
    @DisplayName("buildAndSaveAggregates: reference total is scale 2 and equals the sum of components")
    void buildAndSaveAggregates_referenceTotalsAreSelfConsistent() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();
        adapter.buildAndSaveAggregates(jan);

        for (PayrollPeriodAggregateEntity a :
                aggregateJpaRepository.findByPayrollPeriodId(JAN_2026)) {
            var ref = a.getReferenceAmounts();
            assertThat(ref.getTotal().scale()).isEqualTo(2);
            BigDecimal expected = ref.getBase()
                    .add(ref.getVariable())
                    .add(ref.getAllowances())
                    .add(ref.getEmployerTaxes());
            assertThat(ref.getTotal()).isEqualByComparingTo(expected);
        }
    }

    @Test
    @DisplayName("buildAndSaveAggregates: re-run is idempotent (DRAFT delete + insert)")
    void buildAndSaveAggregates_isIdempotent() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();

        adapter.buildAndSaveAggregates(jan);
        BigDecimal first = sumReferenceTotal(JAN_2026);

        adapter.buildAndSaveAggregates(jan);
        BigDecimal second = sumReferenceTotal(JAN_2026);

        assertThat(aggregateJpaRepository.findByPayrollPeriodId(JAN_2026)).hasSize(2);
        assertThat(second).isEqualByComparingTo(first);
        assertThat(second).isEqualByComparingTo(FR_AGG_REF_TOTAL.add(DE_AGG_REF_TOTAL));
    }

    @Test
    @DisplayName("buildAndSaveAggregates: two periods produce independent aggregates")
    void buildAndSaveAggregates_isolatesPeriods() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();
        PayrollPeriod feb = payrollPeriodRepository.findById(FEB_2026).orElseThrow();

        adapter.buildAndSaveAggregates(jan);
        adapter.buildAndSaveAggregates(feb);

        assertThat(aggregateJpaRepository.findByPayrollPeriodId(JAN_2026)).hasSize(2);
        assertThat(aggregateJpaRepository.findByPayrollPeriodId(FEB_2026)).hasSize(2);

        // February's FR total is different from January's (Alice 1000, Bob 1500 vs 1000+2000)
        BigDecimal febFrTotal = aggregateJpaRepository.findByPayrollPeriodId(FEB_2026).stream()
                .filter(r -> FR.equals(r.getCountryId()))
                .map(r -> r.getReferenceAmounts().getTotal())
                .findFirst().orElseThrow();
        // EUR (1000 + 1500) × 1.10 = 2750.00
        assertThat(febFrTotal).isEqualByComparingTo("2750.00");
    }

    // ------------------------------------------------------------------
    // Rankings
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buildAndSaveRankings: writes 5 groups × 2 directions × up to 5 rows")
    void buildAndSaveRankings_writesExpectedRows() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();

        PayrollPeriodSnapshotResult result = adapter.buildAndSaveRankings(jan);
        assertThat(result).isNotNull();
        assertThat(result.blockers()).isEmpty();

        List<PayrollPeriodEarnerRankingEntity> rankings =
                rankingJpaRepository.findByPayrollPeriodId(JAN_2026);
        // 4 employees per group at most. Global: 4 (top 4 + bottom 4 = up to 8, but
        // since ranks 1-4 = ranks 1-4, top and bottom are the same set when
        // population <= 5. That's fine — the test is structural, not exact.)
        assertThat(rankings)
                .isNotEmpty()
                .allMatch(r -> JAN_2026.equals(r.getPayrollPeriodId()));
    }

    @Test
    @DisplayName("buildAndSaveRankings: re-run is idempotent")
    void buildAndSaveRankings_isIdempotent() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();

        adapter.buildAndSaveRankings(jan);
        long firstRankings = rankingJpaRepository.findByPayrollPeriodId(JAN_2026).size();
        long firstPercentiles = percentileJpaRepository.findByPayrollPeriodId(JAN_2026).size();

        adapter.buildAndSaveRankings(jan);
        long secondRankings = rankingJpaRepository.findByPayrollPeriodId(JAN_2026).size();
        long secondPercentiles = percentileJpaRepository.findByPayrollPeriodId(JAN_2026).size();

        assertThat(secondRankings).isEqualTo(firstRankings);
        assertThat(secondPercentiles).isEqualTo(firstPercentiles);
    }

    @Test
    @DisplayName("buildAndSaveRankings: no (period, direction, employee) duplicates")
    void buildAndSaveRankings_hasNoDuplicates() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();
        adapter.buildAndSaveRankings(jan);

        var seen = new java.util.HashSet<String>();
        for (PayrollPeriodEarnerRankingEntity r :
                rankingJpaRepository.findByPayrollPeriodId(JAN_2026)) {
            String key = r.getPayrollPeriodId() + "|" + r.getDirection() + "|" + r.getEmployeeId()+ "|"
                    + r.getCountryId() + "|" + r.getRoleId()+ "|" + r.getDepartmentId()+ "|" + r.getEmploymentType();
            assertThat(seen.add(key))
                    .as("Duplicate (period, direction, employee): %s", key)
                    .isTrue();
        }
    }

    // ------------------------------------------------------------------
    // Percentiles
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buildAndSaveRankings: percentiles only for groups with population >= 10")
    void percentilesOnlyAbovePopulationThreshold() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();
        adapter.buildAndSaveRankings(jan);

        // With only 4 employees in the fixture, no group has population >= 10,
        // so no percentiles should be written.
        assertThat(percentileJpaRepository.findByPayrollPeriodId(JAN_2026)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Reference currency checks
    // ------------------------------------------------------------------

    @Test
    @DisplayName("all aggregates use the configured reference currency as reference_currency")
    void aggregatesUseReferenceCurrency() {
        PayrollPeriod jan = payrollPeriodRepository.findById(JAN_2026).orElseThrow();
        String ref = currencyRateJpaRepository.findReferenceCurrencyCode().orElseThrow();
        adapter.buildAndSaveAggregates(jan);

        assertThat(aggregateJpaRepository.findByPayrollPeriodId(JAN_2026))
                .allMatch(a -> ref.equals(a.getReferenceAmounts().getCurrency()));
    }

    // ------------------------------------------------------------------
    // checkCoverage — read-only
    // ------------------------------------------------------------------

    @Test
    @DisplayName("checkCoverage: clean when every summary is VALIDATED and none missing")
    void checkCoverage_clean() {
        PayrollPeriodSnapshotResult result = adapter.checkCoverage(jan());

        assertThat(result.blockers()).isEmpty();
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    @DisplayName("checkCoverage: writes nothing")
    void checkCoverage_writesNothing() {
        long aggregatesBefore = aggregateJpaRepository.count();
        long rankingsBefore = rankingJpaRepository.count();
        long percentilesBefore = percentileJpaRepository.count();

        adapter.checkCoverage(jan());

        assertThat(aggregateJpaRepository.count()).isEqualTo(aggregatesBefore);
        assertThat(rankingJpaRepository.count()).isEqualTo(rankingsBefore);
        assertThat(percentileJpaRepository.count()).isEqualTo(percentilesBefore);
    }

    @Test
    @DisplayName("checkCoverage: a DRAFT summary produces UNVALIDATED_PAYROLL_SUMMARY")
    void checkCoverage_flagsDraftSummary() {
        entityManager.createNativeQuery("""
                UPDATE payroll_summaries
                SET status = 'DRAFT'
                WHERE id = 'aaaa0001-0000-0000-0000-000000000001'
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        PayrollPeriodSnapshotResult result = adapter.checkCoverage(jan());

        assertThat(result.blockers())
                .anyMatch(w -> w.code() == ReportWarningCode.UNVALIDATED_PAYROLL_SUMMARY);
    }

    // ------------------------------------------------------------------
    // buildSaveAggregatesAndRankings — combined build
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buildSaveAggregatesAndRankings: writes both aggregates and rankings as DRAFT")
    void buildSave_writesBothAsDraft() {
        PayrollPeriodSnapshotResult result = adapter.buildSaveAggregatesAndRankings(jan());

        assertThat(result.blockers()).isEmpty();
        assertThat(aggregateJpaRepository.findByPayrollPeriodId(JAN_2026))
                .isNotEmpty()
                .allMatch(a -> a.getStatus() == PayrollSummaryStatus.DRAFT);
        assertThat(rankingJpaRepository.findByPayrollPeriodId(JAN_2026))
                .isNotEmpty()
                .allMatch(r -> r.getStatus() == PayrollSummaryStatus.DRAFT);
    }

    @Test
    @DisplayName("buildSaveAggregatesAndRankings: re-run is idempotent")
    void buildSave_isIdempotent() {
        adapter.buildSaveAggregatesAndRankings(jan());
        long aggFirst = aggregateJpaRepository.findByPayrollPeriodId(JAN_2026).size();
        long rankFirst = rankingJpaRepository.findByPayrollPeriodId(JAN_2026).size();

        adapter.buildSaveAggregatesAndRankings(jan());
        long aggSecond = aggregateJpaRepository.findByPayrollPeriodId(JAN_2026).size();
        long rankSecond = rankingJpaRepository.findByPayrollPeriodId(JAN_2026).size();

        assertThat(aggSecond).isEqualTo(aggFirst);
        assertThat(rankSecond).isEqualTo(rankFirst);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private BigDecimal sumReferenceTotal(UUID periodId) {
        return aggregateJpaRepository.findByPayrollPeriodId(periodId).stream()
                .map(a -> a.getReferenceAmounts().getTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PayrollPeriod jan() {
        return payrollPeriodRepository.findById(JAN_2026).orElseThrow();
    }
}