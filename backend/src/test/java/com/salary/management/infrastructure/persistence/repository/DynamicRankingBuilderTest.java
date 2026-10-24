package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.embeddable.AmountsEmbeddable;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link DynamicRankingBuilder} with a mocked EntityManager. */
@ExtendWith(MockitoExtension.class)
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

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Tuple> query;

    @InjectMocks
    private DynamicRankingBuilder builder;

    // ------------------------------------------------------------------
    // Query-shape assertions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("global ranking has no PARTITION BY clause")
    void globalHasNoPartitionBy() {
        stubEmptyQuery();

        builder.rank(JANUARY, "USD", EarnerRankingDirection.TOP, null, 5);

        String jpql = captureJpql();
        assertFalse(jpql.contains("PARTITION BY"));
        assertTrue(jpql.contains("ROW_NUMBER() OVER ("));
        assertTrue(jpql.contains("r.rank <= :limit"));
    }

    @Test
    @DisplayName("country ranking partitions by country")
    void countryPartition() {
        stubEmptyQuery();

        builder.rank(JANUARY, "USD", EarnerRankingDirection.TOP, GroupKey.COUNTRY, 5);

        String jpql = captureJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentCountryId"));
        assertTrue(jpql.contains("r.countryId"));
        assertFalse(jpql.contains("r.departmentId"));
    }

    @Test
    @DisplayName("department ranking partitions by department")
    void departmentPartition() {
        stubEmptyQuery();

        builder.rank(JANUARY, "USD", EarnerRankingDirection.TOP,
                GroupKey.DEPARTMENT, 5);

        String jpql = captureJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentDepartmentId"));
        assertTrue(jpql.contains("r.departmentId"));
        assertFalse(jpql.contains("r.countryId"));
    }

    @Test
    @DisplayName("role ranking partitions by role")
    void rolePartition() {
        stubEmptyQuery();

        builder.rank(JANUARY, "USD", EarnerRankingDirection.TOP, GroupKey.ROLE, 5);

        String jpql = captureJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentRoleId"));
        assertTrue(jpql.contains("r.roleId"));
    }

    @Test
    @DisplayName("employment type ranking partitions by employment type")
    void employmentTypePartition() {
        stubEmptyQuery();

        builder.rank(JANUARY, "USD", EarnerRankingDirection.TOP,
                GroupKey.EMPLOYMENT_TYPE, 5);

        String jpql = captureJpql();
        assertTrue(jpql.contains("PARTITION BY s.assignmentEmploymentType"));
        assertTrue(jpql.contains("r.employmentType"));
    }

    @Test
    @DisplayName("TOP orders by amount descending, BOTTOM ascending")
    void directionControlsOrder() {
        stubEmptyQuery();

        builder.rank(JANUARY, "USD", EarnerRankingDirection.TOP,
                GroupKey.COUNTRY, 5);
        String top = captureJpql();
        assertTrue(top.contains("END DESC"));

        builder.rank(JANUARY, "USD", EarnerRankingDirection.BOTTOM,
                GroupKey.COUNTRY, 5);
        String bottom = captureJpql();
        assertTrue(bottom.contains("END ASC"));
    }

    @Test
    @DisplayName("CURRENCY is rejected as a ranking key")
    void currencyKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                builder.rank(JANUARY, "USD", EarnerRankingDirection.TOP,
                        GroupKey.CURRENCY, 5));
    }

    // ------------------------------------------------------------------
    // Entity mapping — one test per partition kind, asserting the right
    // dimension is set and the others stay null
    // ------------------------------------------------------------------

    @Test
    @DisplayName("COUNTRY row sets countryId only")
    void countryRowSetsOnlyCountry() {
        Tuple row = rankingTuple()
                .withCountry(FR)
                .withEmployee(GRACE)
                .withRank(1)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .build();
        stubSingleRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder.rank(
                JANUARY, "USD", EarnerRankingDirection.TOP, GroupKey.COUNTRY, 5);

        assertEquals(1, rows.size());
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
        assertEquals(JANUARY, e.getPayrollPeriodId());
        assertEquals(PayrollSummaryStatus.VALIDATED, e.getStatus());
    }

    @Test
    @DisplayName("DEPARTMENT row sets departmentId only")
    void departmentRowSetsOnlyDepartment() {
        Tuple row = rankingTuple()
                .withDepartment(ENGINEERING)
                .withEmployee(GRACE)
                .withRank(1)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .build();
        stubSingleRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder.rank(
                JANUARY, "USD", EarnerRankingDirection.TOP, GroupKey.DEPARTMENT, 5);

        assertEquals(1, rows.size());
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
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .build();
        stubSingleRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder.rank(
                JANUARY, "USD", EarnerRankingDirection.TOP, GroupKey.ROLE, 5);

        assertEquals(1, rows.size());
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
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .build();
        stubSingleRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder.rank(
                JANUARY, "USD", EarnerRankingDirection.TOP, GroupKey.EMPLOYMENT_TYPE, 5);

        assertEquals(1, rows.size());
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
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .build();
        stubSingleRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder.rank(
                JANUARY, "USD", EarnerRankingDirection.TOP, null, 5);

        assertEquals(1, rows.size());
        PayrollPeriodEarnerRankingEntity e = rows.get(0);
        assertNull(e.getCountryId());
        assertNull(e.getDepartmentId());
        assertNull(e.getRoleId());
        assertNull(e.getEmploymentType());
        assertNotNull(e.getReferenceAmount());
    }

    @Test
    @DisplayName("BOTTOM direction is written to the entity")
    void bottomDirectionIsWritten() {
        Tuple row = rankingTuple()
                .withCountry(FR)
                .withEmployee(KATHERINE)
                .withRank(1)
                .withOriginal("1080.00", "EUR")
                .withReference("1000.00", "USD")
                .build();
        stubSingleRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder.rank(
                JANUARY, "USD", EarnerRankingDirection.BOTTOM, GroupKey.COUNTRY, 5);

        assertEquals(1, rows.size());
        assertEquals(EarnerRankingDirection.BOTTOM, rows.get(0).getDirection());
    }

    @Test
    @DisplayName("rank is read from the tuple")
    void rankIsReadFromTuple() {
        Tuple row = rankingTuple()
                .withCountry(FR)
                .withEmployee(GRACE)
                .withRank(3)
                .withOriginal("2700.00", "EUR")
                .withReference("2500.00", "USD")
                .build();
        stubSingleRow(row);

        List<PayrollPeriodEarnerRankingEntity> rows = builder.rank(
                JANUARY, "USD", EarnerRankingDirection.TOP, GroupKey.COUNTRY, 5);

        assertEquals(3, rows.get(0).getRank());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubEmptyQuery() {
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    private void stubSingleRow(Tuple row) {
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));
    }

    private String captureJpql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager, org.mockito.Mockito.atLeastOnce())
                .createQuery(captor.capture(), eq(Tuple.class));
        return captor.getValue();
    }

    private RankingTupleBuilder rankingTuple() {
        return new RankingTupleBuilder();
    }

    /**
     * Builder for a mocked {@link Tuple}. Only stubs the aliases that are set; strict
     * stubbing fails on stubs the code under test does not read, which is what we want —
     * it catches "the mapper reads a column the partition did not select".
     */
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

        Tuple build() {
            return tuple;
        }
    }
}