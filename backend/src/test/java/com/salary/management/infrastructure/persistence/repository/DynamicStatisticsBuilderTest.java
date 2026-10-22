package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
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
import java.util.Optional;
import java.util.Set;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link DynamicStatisticsBuilder} with a mocked EntityManager. */
@ExtendWith(MockitoExtension.class)
class DynamicStatisticsBuilderTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ENGINEERING =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID ENGINEER =
            UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID JANUARY =
            UUID.fromString("77777777-7777-7777-7777-777777777777");

    @Mock
    private EntityManager entityManager;

    @Mock
    private TypedQuery<Tuple> query;

    @Mock
    private CountryJpaRepository countryJpaRepository;

    @Mock
    private DepartmentJpaRepository departmentJpaRepository;

    @Mock
    private RoleJpaRepository roleJpaRepository;

    @InjectMocks
    private DynamicStatisticsBuilder builder;

    // ------------------------------------------------------------------
    // Query-shape assertions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("country statistics query groups by country only")
    void countryQueryShape() {
        stubEmptyQuery();

        builder.minMax(List.of(JANUARY), GroupKey.COUNTRY, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        String jpql = captureJpql();
        assertTrue(jpql.contains("r.countryId IS NOT NULL"));
        assertTrue(jpql.contains("r.departmentId IS NULL"));
        assertTrue(jpql.contains("r.roleId IS NULL"));
        assertTrue(jpql.contains("r.employmentType IS NULL"));
        assertTrue(jpql.contains("GROUP BY r.countryId"));
    }

    @Test
    @DisplayName("statistics query filters by period frequency")
    void frequencyFilterIsPresent() {
        stubEmptyQuery();

        builder.minMax(List.of(JANUARY), GroupKey.COUNTRY, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        String jpql = captureJpql();
        assertTrue(jpql.contains("p.frequency = :frequency"));
        verify(query).setParameter("frequency", PayrollPeriodFrequency.MONTHLY);
    }

    @Test
    @DisplayName("bi-weekly frequency is bound when requested")
    void biWeeklyFrequencyIsBound() {
        stubEmptyQuery();

        builder.minMax(List.of(JANUARY), null, PayrollPeriodFrequency.BI_WEEKLY,
                "USD", null, null, null, null);

        verify(query).setParameter("frequency", PayrollPeriodFrequency.BI_WEEKLY);
    }

    @Test
    @DisplayName("global statistics query has no GROUP BY keyword")
    void globalQueryHasNoGroupBy() {
        stubEmptyQuery();

        builder.minMax(List.of(JANUARY), null, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        String jpql = captureJpql();
        assertFalse(jpql.contains("GROUP BY"));
        assertTrue(jpql.contains("r.countryId IS NULL"));
        assertTrue(jpql.contains("r.employmentType IS NULL"));
    }

    @Test
    @DisplayName("country filter is bound only when a country id set is provided")
    void countryFilterBinding() {
        stubEmptyQuery();

        builder.minMax(List.of(JANUARY), GroupKey.COUNTRY, PayrollPeriodFrequency.MONTHLY,
                "USD", Set.of(FR), null, null, null);

        String jpql = captureJpql();
        assertTrue(jpql.contains("r.countryId IN :countryIds"));
        verify(query).setParameter("countryIds", Set.of(FR));
    }

    @Test
    @DisplayName("no country filter means no binding")
    void noCountryFilterNoBinding() {
        stubEmptyQuery();

        builder.minMax(List.of(JANUARY), GroupKey.COUNTRY, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        verify(query, never()).setParameter(eq("countryIds"), any());
    }

    @Test
    @DisplayName("CURRENCY is rejected as a statistics key")
    void currencyKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                builder.minMax(List.of(JANUARY), GroupKey.CURRENCY,
                        PayrollPeriodFrequency.MONTHLY, "USD",
                        null, null, null, null));
    }

    // ------------------------------------------------------------------
    // Projection assertions
    // ------------------------------------------------------------------

    @Test
    @DisplayName("country rows are projected with display values and scaled amounts")
    void countryRowsAreProjected() {
        Tuple frTuple = countryTuple(FR, "1000.00", "2500.00");
        Tuple deTuple = countryTuple(DE, "625.00", "625.00");
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(frTuple, deTuple));
        when(countryJpaRepository.findById(FR)).thenReturn(Optional.of(
                CountryEntity.builder().id(FR).isoCode("FR").name("France").build()));
        when(countryJpaRepository.findById(DE)).thenReturn(Optional.of(
                CountryEntity.builder().id(DE).isoCode("DE").name("Germany").build()));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), GroupKey.COUNTRY, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        assertEquals(2, rows.size());
        AggregateRowProjection fr = rows.stream()
                .filter(r -> "FR".equals(r.country.code()))
                .findFirst()
                .orElseThrow();
        assertEquals("France", fr.country.name());
        assertEquals(0, fr.minReferenceAmount.compareTo(new BigDecimal("1000.00")));
        assertEquals(0, fr.maxReferenceAmount.compareTo(new BigDecimal("2500.00")));
        assertEquals(2, fr.minReferenceAmount.scale());
        assertEquals(2, fr.maxReferenceAmount.scale());

        AggregateRowProjection de = rows.stream()
                .filter(r -> "DE".equals(r.country.code()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, de.minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertEquals(0, de.maxReferenceAmount.compareTo(new BigDecimal("625.00")));
    }

    @Test
    @DisplayName("department rows resolve display values through the department repository")
    void departmentRowsAreProjected() {
        Tuple engTuple = groupTuple("departmentId", ENGINEERING, "1000.00", "2500.00");
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(engTuple));
        when(departmentJpaRepository.findById(ENGINEERING)).thenReturn(Optional.of(
                DepartmentEntity.builder()
                        .id(ENGINEERING).code("ENG").name("Engineering").build()));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), GroupKey.DEPARTMENT, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        assertEquals(1, rows.size());
        AggregateRowProjection eng = rows.get(0);
        assertNotNull(eng.department);
        assertEquals("ENG", eng.department.code());
        assertEquals("Engineering", eng.department.name());
    }

    @Test
    @DisplayName("role rows resolve display values through the role repository")
    void roleRowsAreProjected() {
        Tuple engTuple = groupTuple("roleId", ENGINEER, "1000.00", "2500.00");
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(engTuple));
        when(roleJpaRepository.findById(ENGINEER)).thenReturn(Optional.of(
                RoleEntity.builder()
                        .id(ENGINEER).code("ENG_1").name("Engineer").build()));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), GroupKey.ROLE, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        assertEquals(1, rows.size());
        AggregateRowProjection eng = rows.get(0);
        assertNotNull(eng.role);
        assertEquals("ENG_1", eng.role.code());
        assertEquals("Engineer", eng.role.name());
    }

    @Test
    @DisplayName("employment type rows carry the enum value")
    void employmentTypeRowsAreProjected() {
        Tuple row = employmentTypeTuple("FULL_TIME", "625.00", "2500.00");
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), GroupKey.EMPLOYMENT_TYPE,
                PayrollPeriodFrequency.MONTHLY, "USD",
                null, null, null, null);

        assertEquals(1, rows.size());
        assertEquals(EmploymentType.FULL_TIME, rows.get(0).employmentType);
    }

    @Test
    @DisplayName("global rows carry no group-key values")
    void globalRowsAreProjected() {
        Tuple row = globalTuple("625.00", "2500.00");
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), null, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        assertEquals(1, rows.size());
        AggregateRowProjection g = rows.get(0);
        assertNull(g.country);
        assertNull(g.department);
        assertNull(g.role);
        assertNull(g.employmentType);
        assertEquals(0, g.minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertEquals(0, g.maxReferenceAmount.compareTo(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("a row with both min and max null is dropped")
    void rowWithBothNullIsDropped() {
        Tuple nullRow = globalTuple(null, null);
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(nullRow));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), null, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        assertTrue(rows.isEmpty());
    }

    @Test
    @DisplayName("a row with only max set is kept, min left null")
    void rowWithOnlyMaxIsKept() {
        Tuple row = globalTuple(null, "2500.00");
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), null, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        assertEquals(1, rows.size());
        assertNull(rows.get(0).minReferenceAmount);
        assertEquals(0, rows.get(0).maxReferenceAmount.compareTo(new BigDecimal("2500.00")));
    }

    @Test
    @DisplayName("a row with only min set is kept, max left null")
    void rowWithOnlyMinIsKept() {
        Tuple row = globalTuple("625.00", null);
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(row));

        List<AggregateRowProjection> rows = builder.minMax(
                List.of(JANUARY), null, PayrollPeriodFrequency.MONTHLY,
                "USD", null, null, null, null);

        assertEquals(1, rows.size());
        assertEquals(0, rows.get(0).minReferenceAmount.compareTo(new BigDecimal("625.00")));
        assertNull(rows.get(0).maxReferenceAmount);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void stubEmptyQuery() {
        when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
    }

    private String captureJpql() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createQuery(captor.capture(), eq(Tuple.class));
        return captor.getValue();
    }

    private Tuple countryTuple(UUID countryId, String min, String max) {
        Tuple t = org.mockito.Mockito.mock(Tuple.class);
        when(t.get("countryId", UUID.class)).thenReturn(countryId);
        stubAmounts(t, min, max);
        return t;
    }

    private Tuple groupTuple(String idAlias, UUID id, String min, String max) {
        Tuple t = org.mockito.Mockito.mock(Tuple.class);
        when(t.get(idAlias, UUID.class)).thenReturn(id);
        stubAmounts(t, min, max);
        return t;
    }

    private Tuple employmentTypeTuple(String employmentType, String min, String max) {
        Tuple t = org.mockito.Mockito.mock(Tuple.class);
        when(t.get("employmentType", EmploymentType.class))
                .thenReturn(EmploymentType.valueOf(employmentType));
        stubAmounts(t, min, max);
        return t;
    }

    private Tuple globalTuple(String min, String max) {
        Tuple t = org.mockito.Mockito.mock(Tuple.class);
        stubAmounts(t, min, max);
        return t;
    }

    private void stubAmounts(Tuple t, String min, String max) {
        when(t.get("minReferenceAmount", BigDecimal.class))
                .thenReturn(min == null ? null : new BigDecimal(min));
        when(t.get("maxReferenceAmount", BigDecimal.class))
                .thenReturn(max == null ? null : new BigDecimal(max));
    }
}