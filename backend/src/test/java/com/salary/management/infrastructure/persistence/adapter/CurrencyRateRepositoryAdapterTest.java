package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;
import com.salary.management.infrastructure.persistence.repository.CurrencyRateJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link CurrencyRateRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/currency-rate-adapter.sql")
class CurrencyRateRepositoryAdapterTest {

    private static final UUID EUR_USD =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GBP_USD =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID CAD_USD =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private CurrencyRateRepositoryAdapter adapter;

    @Autowired
    private CurrencyRateJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    // -----------------------------------------------------------------------
    // saveAll (replace-all)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("saveAll persists the batch and findById returns each row")
    void saveAllPersistsBatchAndFindByIdReturnsIt() {
        CurrencyRate rate = CurrencyRate.builder()
                .currencyCode("CHF")
                .referenceCurrencyCode("EUR")
                .rate(new BigDecimal("1.05"))
                .build();

        adapter.saveAll(List.of(rate));
        entityManager.flush();
        entityManager.clear();

        Optional<CurrencyRate> found = adapter.findById(rate.getId());

        assertTrue(found.isPresent());
        assertEquals(rate.getId(), found.get().getId());
        assertEquals("CHF", found.get().getCurrencyCode());
        assertEquals("EUR", found.get().getReferenceCurrencyCode());
        assertEquals(0, found.get().getRate().compareTo(new BigDecimal("1.05")));
        assertEquals(0, found.get().getReferenceAmount().compareTo(BigDecimal.ONE));
    }

    @Test
    @DisplayName("saveAll replaces every stored row, not appends")
    void saveAllReplacesExistingRows() {
        CurrencyRate chf = CurrencyRate.builder()
                .currencyCode("CHF")
                .referenceCurrencyCode("USD")
                .rate(new BigDecimal("0.89"))
                .build();

        adapter.saveAll(List.of(chf));
        entityManager.flush();
        entityManager.clear();

        assertEquals(1, jpaRepository.count());
        assertEquals("CHF", jpaRepository.findAll().get(0).getCurrencyCode());
    }

    @Test
    @DisplayName("saveAll rejects an empty batch and leaves stored rows untouched")
    void saveAllRejectsEmptyBatch() {
        assertThrows(DataIntegrityViolationException.class,
                () -> adapter.saveAll(List.of()));
        entityManager.flush();

        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("saveAll rejects a batch that mixes reference currencies")
    void saveAllRejectsMixedReferenceCurrencies() {
        CurrencyRate usdRef = CurrencyRate.builder()
                .currencyCode("CHF").referenceCurrencyCode("USD")
                .rate(new BigDecimal("0.89")).build();
        CurrencyRate eurRef = CurrencyRate.builder()
                .currencyCode("CHF").referenceCurrencyCode("EUR")
                .rate(new BigDecimal("0.92")).build();

        assertThrows(DataIntegrityViolationException.class,
                () -> adapter.saveAll(List.of(usdRef, eurRef)));

        entityManager.flush();
        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("saveAll persists every row when the batch shares one reference currency")
    void saveAllPersistsConsistentBatch() {
        CurrencyRate chf = CurrencyRate.builder()
                .currencyCode("CHF").referenceCurrencyCode("USD")
                .rate(new BigDecimal("0.89")).build();
        CurrencyRate jpy = CurrencyRate.builder()
                .currencyCode("JPY").referenceCurrencyCode("USD")
                .rate(new BigDecimal("148.00")).build();

        List<CurrencyRate> saved = adapter.saveAll(List.of(chf, jpy));
        entityManager.flush();

        assertEquals(2, saved.size());
        assertEquals(2, jpaRepository.count());
    }

    // -----------------------------------------------------------------------
    // deleteAll
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteAll clears the table so a new reference currency can be established")
    void deleteAllClearsTableAndAllowsNewReference() {
        adapter.deleteAll();
        entityManager.flush();

        assertEquals(0, jpaRepository.count());
        assertNull(adapter.getReferenceCurrencyCode());

        CurrencyRate eurBased = CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("EUR")
                .rate(new BigDecimal("1.07")).build();
        adapter.saveAll(List.of(eurBased));
        entityManager.flush();

        assertEquals(1, jpaRepository.count());
        assertEquals("EUR", adapter.getReferenceCurrencyCode());
    }

    // -----------------------------------------------------------------------
    // getReferenceCurrencyCode
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getReferenceCurrencyCode returns the shared reference of the stored rows")
    void getReferenceCurrencyCodeReturnsStoredReference() {
        assertEquals("USD", adapter.getReferenceCurrencyCode());
    }

    @Test
    @DisplayName("getReferenceCurrencyCode returns null when the table is empty")
    void getReferenceCurrencyCodeReturnsNullWhenEmpty() {
        adapter.deleteAll();
        entityManager.flush();
        entityManager.clear();

        assertNull(adapter.getReferenceCurrencyCode());
    }

    // -----------------------------------------------------------------------
    // reads
    // -----------------------------------------------------------------------

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
                adapter.findByCurrencyCodeAndReferenceCurrencyCode("EUR", "USD");

        assertTrue(found.isPresent());
        assertEquals(EUR_USD, found.get().getId());
    }

    @Test
    @DisplayName("findByCurrencyCodeAndReferenceCurrencyCode returns empty when unknown")
    void findByCurrencyPairReturnsEmptyWhenUnknown() {
        assertTrue(adapter
                .findByCurrencyCodeAndReferenceCurrencyCode("JPY", "USD")
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
        assertEquals("CAD", result.get(0).getCurrencyCode());
        assertEquals("EUR", result.get(1).getCurrencyCode());
        assertEquals("GBP", result.get(2).getCurrencyCode());
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

    // -----------------------------------------------------------------------
    // deleteById
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("deleteById removes the row")
    void deleteByIdRemovesRow() {
        adapter.deleteById(CAD_USD);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(CAD_USD));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(3, jpaRepository.count());
    }

    // -----------------------------------------------------------------------
    // database triggers (bypass the adapter on purpose)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("trigger rejects a direct INSERT with a mixed reference currency")
    void triggerRejectsDirectInsertWithMixedReference() {
        assertThrows(PersistenceException.class, () -> {
            entityManager
                    .createNativeQuery("INSERT INTO currency_rates "
                            + "(id, currency_code, reference_currency_code, "
                            + "reference_amount, rate) VALUES "
                            + "('aaaaaaaa-0000-0000-0000-000000000001', "
                            + "'CHF', 'EUR', 1, 1.05)")
                    .executeUpdate();
            entityManager.flush();
        });
    }

    @Test
    @DisplayName("trigger rejects a direct INSERT with a self-paired currency")
    void triggerRejectsDirectInsertWithSelfPair() {
        assertThrows(PersistenceException.class, () -> {
            entityManager
                    .createNativeQuery("INSERT INTO currency_rates "
                            + "(id, currency_code, reference_currency_code, "
                            + "reference_amount, rate) VALUES "
                            + "('aaaaaaaa-0000-0000-0000-000000000002', "
                            + "'CHF', 'CHF', 1, 1.00)")
                    .executeUpdate();
            entityManager.flush();
        });
    }
}