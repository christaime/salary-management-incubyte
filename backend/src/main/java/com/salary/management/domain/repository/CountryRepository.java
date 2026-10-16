package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.repository.criteria.CountryCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Country} aggregates. */
public interface CountryRepository {

    Country save(Country country);

    Optional<Country> findById(UUID id);

    Optional<Country> findByIsoCode(String isoCode);

    List<Country> list(CountryCriteria criteria);

    DataPage<Country> page(CountryCriteria criteria);

    boolean existsByIsoCode(String isoCode);

    void deleteById(UUID id);
}