package com.salary.management.application.snapshot;

import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodAggregate;
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
    @DisplayName("one USD group of three returns one aggregate and three TOP + three BOTTOM")
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

        assertEquals(6, result.rankings().size());
        long tops = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP).count();
        long bottoms = result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM).count();
        assertEquals(3, tops);
        assertEquals(3, bottoms);
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
        assertEquals(2, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP).count());
        assertEquals(2, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM).count());
    }

    // ------------------------------------------------------------------
    // Case 5 — missing FX rate for the group currency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("missing FX rate yields blocker and null referenceAmounts, but originalAmounts remain")
    @Sql("/sql/snapshot-builder/case-5-missing-fx-rate.sql")
    void missingFxRate() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertEquals(1, result.blockers().size());
        ReportWarning blocker = result.blockers().get(0);
        assertEquals(ReportWarningCode.MISSING_FX_RATE, blocker.code());
        assertEquals("XAF", blocker.details().get("currency"));

        assertEquals(1, result.aggregates().size());
        PayrollPeriodAggregate aggregate = result.aggregates().get(0);
        assertEquals("XAF", aggregate.getOriginalAmounts().getCurrency());
        assertNull(aggregate.getReferenceAmounts());

        assertEquals(3, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.TOP).count());
        assertEquals(3, result.rankings().stream()
                .filter(r -> r.getDirection() == EarnerRankingDirection.BOTTOM).count());
    }

    // ------------------------------------------------------------------
    // Case 6 — two groups, both rate-covered
    // ------------------------------------------------------------------

    @Test
    @DisplayName("two groups with rates produce two aggregates and 6 + 6 rankings")
    @Sql("/sql/snapshot-builder/case-6-two-groups.sql")
    void twoGroups() {
        PayrollPeriod period = payrollPeriodRepository.findById(OPEN_PERIOD).orElseThrow();

        SnapshotBuildResult result = builder.build(period);

        assertTrue(result.blockers().isEmpty());
        assertTrue(result.warnings().isEmpty());
        assertEquals(2, result.aggregates().size());
        result.aggregates().forEach(a -> assertTrue(a.getReferenceAmounts() != null));
        assertEquals(12, result.rankings().size());
    }
}