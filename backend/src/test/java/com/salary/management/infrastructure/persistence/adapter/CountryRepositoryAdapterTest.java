package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.repository.criteria.CountryCriteria;
import com.salary.management.infrastructure.persistence.repository.CountryJpaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Integration tests for {@link CountryRepositoryAdapter}. */
@SpringBootTest
@Transactional
@Sql("/sql/country-adapter.sql")
class CountryRepositoryAdapterTest {

    private static final UUID FR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID DE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID US = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private CountryRepositoryAdapter adapter;

    @Autowired
    private CountryJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("save then findById returns the persisted country")
    void saveThenFindByIdReturnsPersistedCountry() {
        Country country = Country.builder()
                .isoCode("CA")
                .name("Canada")
                .currencyCode("CAD")
                .build();

        adapter.save(country);
        entityManager.flush();
        entityManager.clear();

        Optional<Country> found = adapter.findById(country.getId());

        assertTrue(found.isPresent());
        assertEquals(country.getId(), found.get().getId());
        assertEquals("CA", found.get().getIsoCode());
        assertEquals("Canada", found.get().getName());
        assertEquals("CAD", found.get().getCurrencyCode());
    }

    @Test
    @DisplayName("save on an existing id updates the row")
    void saveOnExistingIdUpdatesRow() {
        Country existing = Country.builder()
                .id(FR)
                .isoCode("FR")
                .name("France")
                .currencyCode("EUR")
                .build();
        Country renamed = existing.withName("French Republic");

        adapter.save(renamed);
        entityManager.flush();
        entityManager.clear();

        Optional<Country> found = adapter.findById(FR);

        assertTrue(found.isPresent());
        assertEquals("French Republic", found.get().getName());
    }

    @Test
    @DisplayName("findById returns empty for a missing id")
    void findByIdReturnsEmptyForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        assertTrue(adapter.findById(missing).isEmpty());
    }

    @Test
    @DisplayName("findByIsoCode returns the matching country")
    void findByIsoCodeReturnsMatchingCountry() {
        Optional<Country> found = adapter.findByIsoCode("DE");

        assertTrue(found.isPresent());
        assertEquals(DE, found.get().getId());
        assertEquals("Germany", found.get().getName());
    }

    @Test
    @DisplayName("findByIsoCode returns empty for an unknown code")
    void findByIsoCodeReturnsEmptyForUnknownCode() {
        assertTrue(adapter.findByIsoCode("ZZ").isEmpty());
    }

    @Test
    @DisplayName("existsByIsoCode reflects the stored rows")
    void existsByIsoCodeReflectsStoredRows() {
        assertTrue(adapter.existsByIsoCode("US"));
        assertFalse(adapter.existsByIsoCode("ZZ"));
    }

    @Test
    @DisplayName("list returns matching rows sorted by the requested field")
    void listReturnsMatchingRowsSorted() {
        CountryCriteria criteria = CountryCriteria.builder()
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        List<Country> result = adapter.list(criteria);

        assertEquals(3, result.size());
        assertEquals("France", result.get(0).getName());
        assertEquals("Germany", result.get(1).getName());
        assertEquals("United States", result.get(2).getName());
    }

    @Test
    @DisplayName("list filters by keyword")
    void listFiltersByKeyword() {
        CountryCriteria criteria = CountryCriteria.builder()
                .keyword("ran")
                .build();

        List<Country> result = adapter.list(criteria);

        assertEquals(1, result.size());
        assertEquals("France", result.get(0).getName());
    }

    @Test
    @DisplayName("list filters by currency code")
    void listFiltersByCurrencyCode() {
        CountryCriteria criteria = CountryCriteria.builder()
                .currencyCode("EUR")
                .build();

        List<Country> result = adapter.list(criteria);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("page returns correct totalElements and totalPages")
    void pageReturnsCorrectTotals() {
        CountryCriteria criteria = CountryCriteria.builder()
                .page(0)
                .size(2)
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<Country> page = adapter.page(criteria);

        assertEquals(3, page.totalElements());
        assertEquals(2, page.totalPages());
        assertEquals(2, page.content().size());
        assertTrue(page.first());
        assertFalse(page.last());
    }

    @Test
    @DisplayName("page returns the last page with the remaining row")
    void pageReturnsLastPage() {
        CountryCriteria criteria = CountryCriteria.builder()
                .page(1)
                .size(2)
                .sortBy("name")
                .sortDirection(SortDirection.ASC)
                .build();

        DataPage<Country> page = adapter.page(criteria);

        assertEquals(1, page.content().size());
        assertFalse(page.first());
        assertTrue(page.last());
    }

    @Test
    @DisplayName("deleteById removes an unreferenced country")
    void deleteByIdRemovesUnreferencedCountry() {
        adapter.deleteById(US);
        entityManager.flush();
        entityManager.clear();

        assertFalse(jpaRepository.existsById(US));
    }

    @Test
    @DisplayName("deleteById is a no-op for a missing id")
    void deleteByIdIsNoOpForMissingId() {
        UUID missing = UUID.fromString("99999999-9999-9999-9999-999999999999");

        adapter.deleteById(missing);

        assertEquals(3, jpaRepository.count());
    }

    @Test
    @DisplayName("deleteById throws when the country is referenced")
    void deleteByIdThrowsWhenReferenced() {
        // Seed an employee that references FR through origin_country_id.
        entityManager.createNativeQuery("""
                INSERT INTO employees (id, matricule, name, email, origin_country_id)
                VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
                        'EMP-0001', 'Ada Lovelace', 'ada@example.com',
                        '11111111-1111-1111-1111-111111111111')
                """).executeUpdate();
        entityManager.flush();
        entityManager.clear();

        assertThrows(DataIntegrityViolationException.class, () -> adapter.deleteById(FR));
        assertTrue(jpaRepository.existsById(FR));
    }

    @Test
    @DisplayName("save generates and persists a fresh id")
    void savePersistsFreshId() {
        Country country = Country.builder()
                .isoCode("IT")
                .name("Italy")
                .currencyCode("EUR")
                .build();

        assertNotNull(country.getId());
        adapter.save(country);

        assertTrue(jpaRepository.existsById(country.getId()));
    }
}