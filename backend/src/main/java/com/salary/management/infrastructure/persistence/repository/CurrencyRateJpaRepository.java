package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.CurrencyRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link CurrencyRateEntity}. */
@Repository
public interface CurrencyRateJpaRepository
        extends JpaRepository<CurrencyRateEntity, UUID>,
        JpaSpecificationExecutor<CurrencyRateEntity> {

    /**
     * Finds a rate by currency pair.
     *
     * @param currencyCode          source currency code
     * @param referenceCurrencyCode reference currency code
     * @return matching rate, or empty
     */
    Optional<CurrencyRateEntity> findByCurrencyCodeAndReferenceCurrencyCode(
            String currencyCode,
            String referenceCurrencyCode);

    /**
     * Finds the reference currency code .
     */
    @Query("SELECT MIN(c.referenceCurrencyCode) FROM CurrencyRateEntity c")
    Optional<String> findReferenceCurrencyCode();
}