package com.salary.management.application.snapshot;

import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.ReportWarningCode;
import com.salary.management.domain.model.values.EmployeeRef;
import com.salary.management.domain.model.values.ReportWarning;
import com.salary.management.domain.model.values.SnapshotBuildResult;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for the snapshot builder, against real repositories and a known seed. */
@SpringBootTest
@Transactional
class SnapshotBuilderTest {

    private static final UUID OPEN_PERIOD =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID CLOSED_PERIOD =
            UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired
    private SnapshotBuilder builder;

    @Autowired
    private PayrollPeriodRepository payrollPeriodRepository;

    // ------------------------------------------------------------------
    // Case 1 — closed period
    // ------------------------------------------------------------------

    @Test
    @DisplayName("closed period returns PERIOD_CLOSED blocker and no aggregates")
    @Sql("/sql/snapshot-builder/case-1-closed-period.sql")
    void closedPeriodBlocked() {
        PayrollPeriod period = payrollPeriodRepository.findById(CLOSED_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertTrue(result.aggregates().isEmpty());
        assertTrue(result.rankings().isEmpty());
        assertEquals(1, result.blockers().size());
        ReportWarning blocker = result.blockers().get(0);
        assertEquals(ReportWarningCode.PERIOD_CLOSED, blocker.code());
        assertEquals(ReportWarning.Severity.BLOCKER, blocker.severity());
        assertTrue(result.isBlocked());
    }

    // ------------------------------------------------------------------
    // Case 2 — open period, no summaries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("open period with no summaries returns a warning and empty aggregates")
    @Sql("/sql/snapshot-builder/case-2-no-summaries.sql")
    void noSummariesAtAll() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertTrue(result.aggregates().isEmpty());
        assertTrue(result.rankings().isEmpty());
        assertTrue(result.blockers().isEmpty());
        assertEquals(1, result.warnings().size());
        ReportWarning warning = result.warnings().get(0);
        assertEquals(ReportWarningCode.MISSING_PAYROLL_SUMMARY, warning.code());
        assertEquals(ReportWarning.Severity.WARNING, warning.severity());
        assertEquals(3, warning.details().get("count"));
        @SuppressWarnings("unchecked")
        List<EmployeeRef> employees = (List<EmployeeRef>) warning.details().get("employees");
        assertEquals(3, employees.size());
        assertTrue(employees.stream()
                .anyMatch(e -> "EMP-000001".equals(e.matricule())));
    }

    // ------------------------------------------------------------------
    // Case 3 — one group, three validated summaries
    // ------------------------------------------------------------------

    @Test
    @DisplayName("one USD group of three returns one aggregate and fifteen TOP + fifteen BOTTOM")
    @Sql("/sql/snapshot-builder/case-3-one-group-three-summaries.sql")
    void oneGroupThreeSummaries() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertTrue(result.blockers().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertEquals(1, result.aggregates().size());
        PayrollPeriodAggregate aggregate = result.aggregates().get(0);
        assertEquals(3, aggregate.getEmployeeCount());
        assertEquals(new BigDecimal("4500.00"), aggregate.getOriginalAmounts().getBase());
        assertEquals(new BigDecimal("450.00"), aggregate.getOriginalAmounts().getVariable());
        assertEquals(new BigDecimal("225.00"), aggregate.getOriginalAmounts().getAllowances());
        assertEquals(new BigDecimal("900.00"), aggregate.getOriginalAmounts().getEmployerTaxes());
        assertEquals(new BigDecimal("6075.00"), aggregate.getOriginalAmounts().getTotal());
        assertEquals("USD", aggregate.getOriginalAmounts().getCurrency());
        assertEquals(new BigDecimal("6075.00"), aggregate.getReferenceAmounts().getTotal());

        // Five keys per direction (global, country, department, role, employment type),
        // three employees per group, so 5 x 3 = 15 rows per direction.
        long tops = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP).count();
        long bottoms = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM).count();
        assertEquals(15, tops);
        assertEquals(15, bottoms);
        assertEquals(30, result.rankings().size());
    }

    @Test
    @DisplayName("rankings carry both the original and the reference amount")
    @Sql("/sql/snapshot-builder/case-3-one-group-three-summaries.sql")
    void rankingsCarryBothAmounts() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        List<PayrollPeriodEarnerRanking> tops = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP)
                .sorted((a, b) -> Integer.compare(a.getRank(), b.getRank()))
                .toList();
        // Fifteen TOP rows, but only the three from the global partition carry rank 1..3
        // for the whole period. Use the global partition by filtering on null dimensions.
        List<PayrollPeriodEarnerRanking> globalTops = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP)
                .filter(r -> r.getCountry() == null && r.getDepartment() == null
                        && r.getRole() == null && r.getEmploymentType() == null)
                .sorted((a, b) -> Integer.compare(a.getRank(), b.getRank()))
                .toList();
        assertEquals(3, globalTops.size());

        PayrollPeriodEarnerRanking top1 = globalTops.get(0);
        assertEquals(1, top1.getRank());
        assertEquals("EMP-000002", top1.getEmployee().getMatricule());
        assertEquals("USD", top1.getOriginalAmount().getCurrency());
        assertEquals("USD", top1.getReferenceAmount().getCurrency());
        assertEquals(0, top1.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("2700.00")));
        assertEquals(0, top1.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("2700.00")));

        PayrollPeriodEarnerRanking top2 = globalTops.get(1);
        assertEquals(2, top2.getRank());
        assertEquals("EMP-000003", top2.getEmployee().getMatricule());
        assertEquals(0, top2.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("2025.00")));

        PayrollPeriodEarnerRanking top3 = globalTops.get(2);
        assertEquals(3, top3.getRank());
        assertEquals("EMP-000001", top3.getEmployee().getMatricule());
        assertEquals(0, top3.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("1350.00")));

        List<PayrollPeriodEarnerRanking> globalBottoms = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM)
                .filter(r -> r.getCountry() == null && r.getDepartment() == null
                        && r.getRole() == null && r.getEmploymentType() == null)
                .sorted((a, b) -> Integer.compare(a.getRank(), b.getRank()))
                .toList();
        assertEquals(3, globalBottoms.size());
        assertEquals("EMP-000001", globalBottoms.get(0).getEmployee().getMatricule());
        assertEquals(0, globalBottoms.get(0).getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("1350.00")));
    }

    // ------------------------------------------------------------------
    // Case 4 — one DRAFT summary blocks the employee, build continues
    // ------------------------------------------------------------------

    @Test
    @DisplayName("unvalidated summary produces a blocker and only the valid rows are aggregated")
    @Sql("/sql/snapshot-builder/case-4-unvalidated-summary.sql")
    void unvalidatedSummary() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertEquals(1, result.blockers().size());
        ReportWarning blocker = result.blockers().get(0);
        assertEquals(ReportWarningCode.UNVALIDATED_PAYROLL_SUMMARY, blocker.code());
        assertEquals(ReportWarning.Severity.BLOCKER, blocker.severity());
        assertEquals(1, blocker.details().get("count"));
        @SuppressWarnings("unchecked")
        List<EmployeeRef> employees = (List<EmployeeRef>) blocker.details().get("employees");
        assertEquals(1, employees.size());
        assertEquals("EMP-000003", employees.get(0).matricule());

        assertEquals(1, result.aggregates().size());
        assertEquals(2, result.aggregates().get(0).getEmployeeCount());
        // 2 employees x 5 keys per direction.
        assertEquals(10, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP).count());
        assertEquals(10, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM).count());
    }

    // ------------------------------------------------------------------
    // Case 5 — missing FX rate for the group currency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("mixed groups: rate-present group ranks, rate-missing group does not")
    @Sql("/sql/snapshot-builder/case-5-missing-fx-rate.sql")
    void missingFxRate() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertEquals(1, result.blockers().size());
        ReportWarning blocker = result.blockers().get(0);
        assertEquals(ReportWarningCode.MISSING_FX_RATE, blocker.code());
        assertEquals("XAF", blocker.details().get("currency"));

        assertEquals(2, result.aggregates().size());
        PayrollPeriodAggregate fr = result.aggregates().stream()
                .filter(a -> "EUR".equals(a.getOriginalAmounts().getCurrency()))
                .findFirst().orElseThrow();
        PayrollPeriodAggregate cm = result.aggregates().stream()
                .filter(a -> "XAF".equals(a.getOriginalAmounts().getCurrency()))
                .findFirst().orElseThrow();
        assertEquals("USD", fr.getReferenceAmounts().getCurrency());
        assertNull(cm.getReferenceAmounts());
        assertEquals(3, fr.getEmployeeCount());
        assertEquals(3, cm.getEmployeeCount());

        // Only FR rows survive the ranking query's rate filter. 3 employees x 5 keys.
        assertEquals(15, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP).count());
        assertEquals(15, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM).count());
        assertTrue(result.rankings().stream()
                .allMatch(r -> "EUR".equals(r.getOriginalAmount().getCurrency())));
        assertTrue(result.rankings().stream()
                .allMatch(r -> "USD".equals(r.getReferenceAmount().getCurrency())));

        // The global TOP rank 1 is the highest FR total.
        PayrollPeriodEarnerRanking frTop1 = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP)
                .filter(r -> r.getRank() == 1)
                .filter(r -> r.getCountry() == null && r.getDepartment() == null
                        && r.getRole() == null && r.getEmploymentType() == null)
                .findFirst()
                .orElseThrow();
        assertEquals(0, frTop1.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("2700.00")));
        assertEquals(0, frTop1.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("2903.23")));
    }

    // ------------------------------------------------------------------
    // Case 6 — two groups, both rate-covered
    // ------------------------------------------------------------------

    @Test
    @DisplayName("two groups with rates produce two aggregates and twenty top + twenty bottom")
    @Sql("/sql/snapshot-builder/case-6-two-groups.sql")
    void twoGroups() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertTrue(result.blockers().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertEquals(2, result.aggregates().size());
        result.aggregates().forEach(a -> assertTrue(a.getReferenceAmounts() != null));

        long tops = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP).count();
        long bottoms = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM).count();
        // Per direction: global 5 (limit), country 3+3=6, department 5, role 5,
        // employment type 5 = 26. Two directions = 52.
        assertEquals(26, tops);
        assertEquals(26, bottoms);
        assertEquals(52, result.rankings().size());
    }

    @Test
    @DisplayName("XAF ranking entries carry the XAF original and the USD reference amount")
    @Sql("/sql/snapshot-builder/case-6-two-groups.sql")
    void xafRankingHasConvertedReferenceAmount() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        PayrollPeriodEarnerRanking xafTop1 = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP)
                .filter(r -> "XAF".equals(r.getOriginalAmount().getCurrency()))
                .filter(r -> r.getCountry() != null)
                .filter(r -> r.getDepartment() == null)
                .filter(r -> r.getRole() == null)
                .filter(r -> r.getEmploymentType() == null)
                .filter(r -> r.getRank() == 1)
                .findFirst()
                .orElseThrow();

        assertEquals("XAF", xafTop1.getOriginalAmount().getCurrency());
        assertEquals(0, xafTop1.getOriginalAmount().getAmount()
                .compareTo(new BigDecimal("1575477.00")));
        assertEquals("USD", xafTop1.getReferenceAmount().getCurrency());
        assertEquals(0, xafTop1.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("2700.00")));
    }
}