package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.projection.SummaryValidationRow;
import com.salary.management.infrastructure.persistence.repository.PayrollSummaryJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests for {@link PayrollSummaryRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/payroll-summary-adapter.sql")
class PayrollSummaryRepositoryAdapterTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ENGINEERING =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENGINEER =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID JANUARY =
            UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID FEBRUARY =
            UUID.fromString("99999999-9999-9999-9999-999999999999");
    private static final UUID ASSIGNMENT =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID SUMMARY =
            UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID ADA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID GRACE =
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID GRACE_ASSIGNMENT =
            UUID.fromString("55555555-5555-5555-5555-555555555556");

    // Period date bounds, used as the range for findValidationRows.
    private static final LocalDate JANUARY_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate JANUARY_END   = LocalDate.of(2026, 1, 31);
    private static final LocalDate FEBRUARY_START = LocalDate.of(2026, 2, 1);
    private static final LocalDate FEBRUARY_END   = LocalDate.of(2026, 2, 28);

    @Autowired
    private PayrollSummaryRepositoryAdapter adapter;

    @Autowired
    private PayrollSummaryJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    // ------------------------------------------------------------------
    // CRUD
    // ------------------------------------------------------------------

    @Test
    @DisplayName("save then findById returns the persisted summary")
    void saveThenFindByIdReturnsPersistedSummary() {
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(GRACE)
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        FEBRUARY_START, FEBRUARY_END))
                .employeePeriodicAssignmentId(ASSIGNMENT)
                .amounts(amounts("EUR"))
                .status(PayrollSummaryStatus.VALIDATED)
                .build();

        adapter.save(summary);
        entityManager.flush();
        entityManager.clear();

        Optional<PayrollSummary> found = adapter.findById(summary.getId());

        assertTrue(found.isPresent());
        assertEquals(summary.getId(), found.get().getId());
        assertEquals(GRACE, found.get().getEmployeeId());
        assertEquals(FEBRUARY, found.get().getPayrollPeriod().getId());
        assertEquals(ASSIGNMENT, found.get().getEmployeePeriodicAssignmentId());
        assertEquals(PayrollSummaryStatus.VALIDATED, found.get().getStatus());
        assertEquals(0, found.get().getAmounts().getTotal().compareTo(new BigDecimal("1350.00")));
    }

    @Test
    @DisplayName("save throws when the employee does not exist")
    void saveThrowsWhenEmployeeMissing() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(missing)
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        JANUARY_START, JANUARY_END))
                .employeePeriodicAssignmentId(ASSIGNMENT)
                .amounts(amounts("EUR"))
                .status(PayrollSummaryStatus.DRAFT)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.save(summary));
    }

    @Test
    @DisplayName("save throws when the assignment does not exist")
    void saveThrowsWhenAssignmentMissing() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(ADA)
                .payrollPeriod(payrollPeriod(JANUARY, "January 2026",
                        JANUARY_START, JANUARY_END))
                .employeePeriodicAssignmentId(missing)
                .amounts(amounts("EUR"))
                .status(PayrollSummaryStatus.DRAFT)
                .build();

        assertThrows(EntityNotFoundException.class, () -> adapter.save(summary));
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByEmployeeIdAndPayrollPeriodId returns the matching summary")
    void findByEmployeeAndPeriodReturnsSummary() {
        Optional<PayrollSummary> found =
                adapter.findByEmployeeIdAndPayrollPeriodId(ADA, JANUARY);

        assertTrue(found.isPresent());
        assertEquals(SUMMARY, found.get().getId());
    }

    @Test
    @DisplayName("findByEmployeeIdAndPayrollPeriodId returns empty when unknown")
    void findByEmployeeAndPeriodReturnsEmpty() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");

        assertTrue(adapter.findByEmployeeIdAndPayrollPeriodId(ADA, missing).isEmpty());
    }

    @Test
    @DisplayName("deleteById removes the row")
    void deleteByIdRemovesRow() {
        adapter.deleteById(SUMMARY);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(SUMMARY));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999998-9999-9999-9999-999999999998");

        adapter.deleteById(missing);

        assertEquals(1, jpaRepository.count());
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        PayrollSummary summary = PayrollSummary.builder()
                .employeeId(GRACE)
                .payrollPeriod(payrollPeriod(FEBRUARY, "February 2026",
                        FEBRUARY_START, FEBRUARY_END))
                .employeePeriodicAssignmentId(ASSIGNMENT)
                .amounts(amounts("USD"))
                .status(PayrollSummaryStatus.DRAFT)
                .build();

        assertNotNull(summary.getId());
        adapter.save(summary);

        assertTrue(jpaRepository.existsById(summary.getId()));
    }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .sortBy("status")
                .sortDirection(SortDirection.ASC)
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals(SUMMARY, result.get(0).getId());
    }

    @Test
    @DisplayName("list filters by employee id")
    void listFiltersByEmployeeId() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .employeeId(ADA)
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by employee matricule")
    void listFiltersByEmployeeMatricule() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .employeeMatricule("EMP-0001")
                .rangeStart(JANUARY_START)
                .rangeEnd(JANUARY_END)
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by date range")
    void listFiltersByDateRange() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .rangeStart(JANUARY_START)
                .rangeEnd(JANUARY_END)
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by assignment country")
    void listFiltersByAssignmentCountry() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .anyCountryIds(Set.of(FR))
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by assignment department")
    void listFiltersByAssignmentDepartment() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .anyDepartmentIds(Set.of(ENGINEERING))
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("list filters by assignment role")
    void listFiltersByAssignmentRole() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .anyRoleIds(Set.of(ENGINEER))
                .build();

        List<PayrollSummary> result = adapter.list(criteria);

        assertEquals(1, result.size());
    }

    // ------------------------------------------------------------------
    // page
    // ------------------------------------------------------------------

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .page(0)
                .size(10)
                .sortBy("status")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<PayrollSummary> page = adapter.page(criteria);

        assertEquals(1, page.totalElements());
        assertEquals(1, page.totalPages());
        assertEquals(1, page.content().size());
        assertTrue(page.first());
        assertTrue(page.last());
    }

    // ------------------------------------------------------------------
    // pageNonExistent
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pageNonExistent returns synthetic summaries for employees missing a summary")
    void pageNonExistentReturnsSyntheticSummaries() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .nonExistent(true)
                .page(0)
                .size(10)
                .build();

        DataPage<PayrollSummary> page = adapter.pageNonExistent(criteria);

        assertEquals(1, page.totalElements());
        assertEquals(1, page.content().size());
        PayrollSummary synthetic = page.content().get(0);

        assertEquals(GRACE, synthetic.getEmployeeId());
        assertEquals(GRACE_ASSIGNMENT, synthetic.getEmployeePeriodicAssignmentId());
        assertEquals(JANUARY, synthetic.getPayrollPeriod().getId());
        assertEquals(new UUID(0L, 0L), synthetic.getId());

        Amounts amounts = synthetic.getAmounts();
        assertNotNull(amounts);
        assertEquals(0, amounts.getBase().compareTo(BigDecimal.ZERO));
        assertEquals(0, amounts.getVariable().compareTo(BigDecimal.ZERO));
        assertEquals(0, amounts.getAllowances().compareTo(BigDecimal.ZERO));
        assertEquals(0, amounts.getEmployerTaxes().compareTo(BigDecimal.ZERO));
        assertEquals(0, amounts.getTotal().compareTo(BigDecimal.ZERO));
        assertEquals("EUR", amounts.getCurrency());
        assertEquals(PayrollSummaryStatus.DRAFT, synthetic.getStatus());
    }

    @Test
    @DisplayName("pageNonExistent excludes employees who already have a summary")
    void pageNonExistentExcludesEmployeesWithSummary() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .nonExistent(true)
                .page(0)
                .size(10)
                .build();

        DataPage<PayrollSummary> page = adapter.pageNonExistent(criteria);

        assertEquals(1, page.totalElements());
        assertTrue(page.content().stream().noneMatch(s -> ADA.equals(s.getEmployeeId())));
        assertTrue(page.content().stream().allMatch(s -> GRACE.equals(s.getEmployeeId())));
    }

    @Test
    @DisplayName("pageNonExistent throws when nonExistent is false")
    void pageNonExistentThrowsWhenNotNonExistent() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .payrollPeriodId(JANUARY)
                .page(0)
                .size(10)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> adapter.pageNonExistent(criteria));
    }

    @Test
    @DisplayName("pageNonExistent supports an explicit date range without a period id")
    void pageNonExistentSupportsDateRange() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .rangeStart(JANUARY_START)
                .rangeEnd(JANUARY_END)
                .nonExistent(true)
                .page(0)
                .size(10)
                .build();

        DataPage<PayrollSummary> page = adapter.pageNonExistent(criteria);

        assertEquals(1, page.totalElements());
        assertEquals(GRACE, page.content().get(0).getEmployeeId());
    }

    @Test
    @DisplayName("pageNonExistent returns one stub per missing period in a multi-period range")
    void pageNonExistentReturnsStubPerMissingPeriod() {
        PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                .rangeStart(JANUARY_START)
                .rangeEnd(FEBRUARY_END)
                .nonExistent(true)
                .page(0)
                .size(10)
                .build();

        DataPage<PayrollSummary> page = adapter.pageNonExistent(criteria);

        assertEquals(3, page.totalElements());

        assertTrue(page.content().stream().anyMatch(s ->
                        ADA.equals(s.getEmployeeId()) && FEBRUARY.equals(s.getPayrollPeriod().getId())),
                "Ada must have a stub for February");
        assertTrue(page.content().stream().anyMatch(s ->
                        GRACE.equals(s.getEmployeeId()) && JANUARY.equals(s.getPayrollPeriod().getId())),
                "Grace must have a stub for January");
        assertTrue(page.content().stream().anyMatch(s ->
                        GRACE.equals(s.getEmployeeId()) && FEBRUARY.equals(s.getPayrollPeriod().getId())),
                "Grace must have a stub for February");
        assertTrue(page.content().stream().noneMatch(s ->
                        ADA.equals(s.getEmployeeId()) && JANUARY.equals(s.getPayrollPeriod().getId())),
                "Ada already has a January summary, so no stub");
    }

    // ------------------------------------------------------------------
    // latestLinkedPeriodStart
    // ------------------------------------------------------------------

    @Test
    @DisplayName("latestLinkedPeriodStart returns the latest linked period's start date")
    void latestLinkedPeriodStartReturnsLatest() {
        UUID assignmentId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1");

        entityManager.createNativeQuery("""
                INSERT INTO payroll_periods
                    (id, name, start_date, end_date, frequency, closed)
                VALUES ('aaaaaaaa-0000-0000-0000-000000000001',
                        'TEST 2026-04', '2026-04-01', '2026-04-30', 'MONTHLY', 0),
                       ('aaaaaaaa-0000-0000-0000-000000000002',
                        'TEST 2026-05', '2026-05-01', '2026-05-31', 'MONTHLY', 0)
                """).executeUpdate();
        entityManager.createNativeQuery("""
                INSERT INTO payroll_summaries
                    (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
                     assignment_country_id, assignment_department_id, assignment_role_id,
                     assignment_employment_type, base, variable, allowances,
                     employer_taxes, total, currency, status)
                VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1',
                        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        'aaaaaaaa-0000-0000-0000-000000000001',
                        'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1',
                        '11111111-1111-1111-1111-111111111111',
                        '22222222-2222-2222-2222-222222222222',
                        '33333333-3333-3333-3333-333333333333',
                        'FULL_TIME', 1000, 100, 50, 200, 1350, 'EUR', 'VALIDATED'),
                       ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2',
                        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        'aaaaaaaa-0000-0000-0000-000000000002',
                        'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1',
                        '11111111-1111-1111-1111-111111111111',
                        '22222222-2222-2222-2222-222222222222',
                        '33333333-3333-3333-3333-333333333333',
                        'FULL_TIME', 1000, 100, 50, 200, 1350, 'EUR', 'VALIDATED')
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        LocalDate latest = adapter.latestLinkedPeriodStart(assignmentId);

        assertEquals(LocalDate.of(2026, 5, 1), latest);
    }

    @Test
    @DisplayName("latestLinkedPeriodStart returns null when no VALIDATED summary is linked")
    void latestLinkedPeriodStartNullWhenNone() {
        UUID missing = UUID.fromString("99999999-9998-9999-9999-999999999998");

        assertNull(adapter.latestLinkedPeriodStart(missing));
    }

    @Test
    @DisplayName("latestLinkedPeriodStart ignores DRAFT summaries")
    void latestLinkedPeriodStartIgnoresDrafts() {
        UUID assignmentId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        entityManager.createNativeQuery("""
                INSERT INTO payroll_summaries
                    (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
                     assignment_country_id, assignment_department_id, assignment_role_id,
                     assignment_employment_type, base, variable, allowances,
                     employer_taxes, total, currency, status)
                VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3',
                        'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        '99999999-9999-9999-9999-999999999999',
                        '55555555-5555-5555-5555-555555555555',
                        '11111111-1111-1111-1111-111111111111',
                        '22222222-2222-2222-2222-222222222222',
                        '33333333-3333-3333-3333-333333333333',
                        'FULL_TIME', 1000, 100, 50, 200, 1350, 'EUR', 'DRAFT')
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertNull(adapter.latestLinkedPeriodStart(assignmentId));
    }

    // ------------------------------------------------------------------
    // findValidationRows (new signature: range, no status)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findValidationRows returns one row per DRAFT summary in the period")
    void findValidationRowsReturnsDraftRows() {
        DataPage<SummaryValidationRow> page = adapter.findValidationRows(
                JANUARY_START,
                JANUARY_END,
                null,
                null, null, null, null,
                null,
                null, 0, 10);

        assertEquals(1, page.totalElements());
        assertEquals(1, page.content().size());

        SummaryValidationRow row = page.content().get(0);
        assertEquals(SUMMARY, row.summaryId());
        assertEquals(PayrollSummaryStatus.DRAFT, row.status());
        assertEquals(JANUARY, row.periodId());
        assertEquals(Boolean.FALSE, row.periodClosed());
        assertEquals(JANUARY_START, row.periodStart());
        assertEquals(JANUARY_END, row.periodEnd());
        assertEquals(ASSIGNMENT, row.assignmentId());
        assertEquals(Boolean.TRUE, row.assignmentInEffect());
        assertEquals(JANUARY_START, row.assignmentStart());
        assertNull(row.assignmentEnd());
    }

    @Test
    @DisplayName("findValidationRows never returns non-DRAFT summaries")
    void findValidationRowsExcludesNonDraftSummaries() {
        // Flip Ada's January summary to VALIDATED, then query January.
        // The query hard-codes DRAFT, so nothing should match.
        entityManager.createNativeQuery("""
                UPDATE payroll_summaries SET status = 'VALIDATED'
                 WHERE id = '66666666-6666-6666-6666-666666666666'
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        DataPage<SummaryValidationRow> page = adapter.findValidationRows(
                JANUARY_START,
                JANUARY_END,
                null,
                null, null, null, null,
                null,
                null, 0, 10);

        assertTrue(page.content().isEmpty());
    }

    @Test
    @DisplayName("findValidationRows scopes to periods contained in the range")
    void findValidationRowsScopesToRange() {
        // February contains no DRAFT summary in the fixture.
        DataPage<SummaryValidationRow> page = adapter.findValidationRows(
                FEBRUARY_START,
                FEBRUARY_END,
                null,
                null, null, null, null,
                null,
                null, 0, 10);

        assertTrue(page.content().isEmpty());
    }

    @Test
    @DisplayName("findValidationRows applies the optional assignment filters")
    void findValidationRowsAppliesAssignmentFilters() {
        assertEquals(1, adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                Set.of(FR), null, null, null, null,
                null, 0, 10).content().size());

        assertEquals(1, adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                null, Set.of(ENGINEERING), null, null, null,
                null, 0, 10).content().size());

        assertEquals(1, adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                null, null, Set.of(ENGINEER), null, null,
                null, 0, 10).content().size());

        assertEquals(1, adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                null, null, null, Set.of(EmploymentType.FULL_TIME), null,
                null, 0, 10).content().size());

        UUID otherCountry = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        assertTrue(adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                Set.of(otherCountry), null, null, null, null,
                null, 0, 10).content().isEmpty());
    }

    @Test
    @DisplayName("findValidationRows applies the assignment frequency filter")
    void findValidationRowsAppliesFrequencyFilter() {
        assertEquals(1, adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                null, null, null, null,
                PayrollPeriodFrequency.MONTHLY,
                null, 0, 10).content().size());

        assertTrue(adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                null, null, null, null,
                PayrollPeriodFrequency.BI_WEEKLY,
                null, 0, 10).content().isEmpty());
    }

    @Test
    @DisplayName("findValidationRows filters by employee id")
    void findValidationRowsFiltersByEmployeeId() {
        assertEquals(1, adapter.findValidationRows(
                JANUARY_START, JANUARY_END, ADA,
                null, null, null, null, null,
                null, 0, 10).content().size());

        assertTrue(adapter.findValidationRows(
                JANUARY_START, JANUARY_END, GRACE,
                null, null, null, null, null,
                null, 0, 10).content().isEmpty());
    }

    @Test
    @DisplayName("findValidationRows keyset-paginates on summary id without repeats")
    void findValidationRowsKeysetPaginates() {
        // Bring the January period to 3 DRAFT summaries for 3 distinct employees
        // (Ada already has one; add Grace and Katherine) so the unique
        // (employee_id, payroll_period_id) constraint is satisfied.
        entityManager.createNativeQuery("""
            INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id)
            VALUES ('cccccccc-cccc-cccc-cccc-cccccccccccc',
                    'EMP-0003', 'Katherine Johnson', 'katherine@example.com',
                    '1918-08-26', '11111111-1111-1111-1111-111111111111')
            """).executeUpdate();

        entityManager.createNativeQuery("""
            INSERT INTO employee_periodic_assignments
                (id, employee_id, start_date, end_date, country_id,
                 department_id, role_id, employment_type, frequency, in_effect)
            VALUES ('55555555-5555-5555-5555-555555555557',
                    'cccccccc-cccc-cccc-cccc-cccccccccccc',
                    '2026-01-01', NULL,
                    '11111111-1111-1111-1111-111111111111',
                    '22222222-2222-2222-2222-222222222222',
                    '33333333-3333-3333-3333-333333333333',
                    'FULL_TIME', 'MONTHLY', 1)
            """).executeUpdate();

        entityManager.createNativeQuery("""
            INSERT INTO payroll_summaries
                (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
                 assignment_country_id, assignment_department_id, assignment_role_id,
                 assignment_employment_type, base, variable, allowances,
                 employer_taxes, total, currency, status)
            VALUES ('77777777-7777-7777-7777-777777777777',
                    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
                    '44444444-4444-4444-4444-444444444444',
                    '55555555-5555-5555-5555-555555555556',
                    '11111111-1111-1111-1111-111111111111',
                    '22222222-2222-2222-2222-222222222222',
                    '33333333-3333-3333-3333-333333333333',
                    'FULL_TIME', 1000, 100, 50, 200, 1350, 'EUR', 'DRAFT'),
                   ('88888888-8888-8888-8888-888888888888',
                    'cccccccc-cccc-cccc-cccc-cccccccccccc',
                    '44444444-4444-4444-4444-444444444444',
                    '55555555-5555-5555-5555-555555555557',
                    '11111111-1111-1111-1111-111111111111',
                    '22222222-2222-2222-2222-222222222222',
                    '33333333-3333-3333-3333-333333333333',
                    'FULL_TIME', 1000, 100, 50, 200, 1350, 'EUR', 'DRAFT')
            """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        DataPage<SummaryValidationRow> first = adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                null, null, null, null, null,
                null, 0, 2);

        assertEquals(2, first.content().size());
        assertEquals(3, first.totalElements());

        UUID cursor = first.content().get(first.content().size() - 1).summaryId();
        DataPage<SummaryValidationRow> second = adapter.findValidationRows(
                JANUARY_START, JANUARY_END, null,
                null, null, null, null, null,
                cursor, 0, 2);

        assertEquals(1, second.content().size());

        Set<UUID> firstIds = first.content().stream()
                .map(SummaryValidationRow::summaryId)
                .collect(Collectors.toSet());
        Set<UUID> secondIds = second.content().stream()
                .map(SummaryValidationRow::summaryId)
                .collect(Collectors.toSet());
        assertTrue(firstIds.stream().noneMatch(secondIds::contains));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PayrollPeriod payrollPeriod(UUID id, String name,
                                        LocalDate start, LocalDate end) {
        return PayrollPeriod.builder()
                .id(id)
                .name(name)
                .startDate(start)
                .endDate(end)
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }

    private Amounts amounts(String currency) {
        return Amounts.builder()
                .base(new BigDecimal("1000.00"))
                .variable(new BigDecimal("100.00"))
                .allowances(new BigDecimal("50.00"))
                .employerTaxes(new BigDecimal("200.00"))
                .currency(currency)
                .build();
    }
}