package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link CountryEntity}. */
@Repository
public interface CountryJpaRepository
        extends JpaRepository<CountryEntity, UUID>, JpaSpecificationExecutor<CountryEntity> {

    /**
     * Finds a country by ISO code.
     *
     * @param isoCode ISO code
     * @return matching country, or empty
     */
    Optional<CountryEntity> findByIsoCode(String isoCode);

    /**
     * Returns whether a country with the given ISO code exists.
     *
     * @param isoCode ISO code
     * @return {@code true} when present
     */
    boolean existsByIsoCode(String isoCode);
}