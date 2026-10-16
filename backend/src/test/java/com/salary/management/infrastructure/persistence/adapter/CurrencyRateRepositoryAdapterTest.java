package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;
import com.salary.management.infrastructure.persistence.repository.CurrencyRateJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link CurrencyRateRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/currency-rate-adapter.sql")
class CurrencyRateRepositoryAdapterTest {

    private static final UUID EUR_USD =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USD_EUR =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Autowired
    private CurrencyRateRepositoryAdapter adapter;

    @Autowired
    private CurrencyRateJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted rate")
    void saveThenFindByIdReturnsPersistedRate() {
        CurrencyRate rate = CurrencyRate.builder()
                .currencyCode("CHF")
                .referenceCurrencyCode("EUR")
                .rate(new BigDecimal("1.05"))
                .build();

        adapter.save(rate);
        entityManager.flush();
        entityManager.clear();

        Optional<CurrencyRate> found = adapter.findById(rate.getId());

        assertTrue(found.isPresent());
        assertEquals(rate.getId(), found.get().getId());
        assertEquals("CHF", found.get().getCurrencyCode());
        assertEquals("EUR", found.get().getReferenceCurrencyCode());
        assertEquals(0, found.get().getRate().compareTo(new BigDecimal("1.05")));
    }

    @Test
    @DisplayName("save on an existing id updates the row")
    void saveOnExistingIdUpdatesRow() {
        CurrencyRate existing = CurrencyRate.builder()
                .id(EUR_USD)
                .currencyCode("EUR")
                .referenceCurrencyCode("USD")
                .rate(new BigDecimal("1.08"))
                .build();
        CurrencyRate updated = existing.withRate(new BigDecimal("1.10"));

        adapter.save(updated);
        entityManager.flush();
        entityManager.clear();

        Optional<CurrencyRate> found = adapter.findById(EUR_USD);

        assertTrue(found.isPresent());
        assertEquals(0, found.get().getRate().compareTo(new BigDecimal("1.10")));
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByCurrencyCodeAndReferenceCurrencyCode returns the matching rate")
    void findByCurrencyPairReturnsMatchingRate() {
        Optional<CurrencyRate> found =
                adapter.findByCurrencyCodeAndReferenceCurrencyCode("USD", "EUR");

        assertTrue(found.isPresent());
        assertEquals(USD_EUR, found.get().getId());
    }

    @Test
    @DisplayName("findByCurrencyCodeAndReferenceCurrencyCode returns empty when unknown")
    void findByCurrencyPairReturnsEmptyWhenUnknown() {
        assertTrue(adapter
                .findByCurrencyCodeAndReferenceCurrencyCode("JPY", "EUR")
                .isEmpty());
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        CurrencyRateCriteria criteria = CurrencyRateCriteria.builder()
                .sortBy("currencyCode")
                .sortDirection(SortDirection.ASC)
                .build();

        List<CurrencyRate> result = adapter.list(criteria);

        assertEquals(3, result.size());
        assertEquals("EUR", result.get(0).getCurrencyCode());
        assertEquals("GBP", result.get(1).getCurrencyCode());
        assertEquals("USD", result.get(2).getCurrencyCode());
    }

    @Test
    @DisplayName("list filters by keyword")
    void listFiltersByKeyword() {
        CurrencyRateCriteria criteria = CurrencyRateCriteria.builder()
                .keyword("gbp")
                .build();

        List<CurrencyRate> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("GBP", result.get(0).getCurrencyCode());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        CurrencyRateCriteria criteria = CurrencyRateCriteria.builder()
                .page(0)
                .size(2)
                .sortBy("currencyCode")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<CurrencyRate> page = adapter.page(criteria);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(2, page.content().size());
        assertTrue(page.first());
        assertFalse(page.last());
    }

    @Test
    @DisplayName("page returns the last page with the remaining row")
    void pageReturnsLastPage() {
        CurrencyRateCriteria criteria = CurrencyRateCriteria.builder()
                .page(1)
                .size(2)
                .sortBy("currencyCode")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<CurrencyRate> page = adapter.page(criteria);

        assertEquals(1, page.content().size());
        assertFalse(page.first());
        assertTrue(page.last());
    }

    @Test
    @DisplayName("deleteById removes the row")
    void deleteByIdRemovesRow() {
        UUID gbpEur = UUID.fromString("33333333-3333-3333-3333-333333333333");

        adapter.deleteById(gbpEur);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(gbpEur));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        CurrencyRate rate = CurrencyRate.builder()
                .currencyCode("CAD")
                .referenceCurrencyCode("EUR")
                .rate(new BigDecimal("0.68"))
                .build();

        assertNotNull(rate.getId());
        adapter.save(rate);

        assertTrue(jpaRepository.existsById(rate.getId()));
    }
}