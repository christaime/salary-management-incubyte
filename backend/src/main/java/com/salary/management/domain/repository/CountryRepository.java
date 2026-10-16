package com.salary.management.domain.repository;

import com.salary.management.domain.model.Country;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Country} aggregates.
 */
public interface CountryRepository {

    Country save(Country country);

    Optional<Country> findById(UUID id);

    Optional<Country> findByIsoCode(String isoCode);

    List<Country> findAll();

    boolean existsByIsoCode(String isoCode);
}