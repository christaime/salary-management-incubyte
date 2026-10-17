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
     * Reference currency shared by every stored row, or {@code null} when the table is empty.
     *
     * <p>Read helper for the adapter's {@code getReferenceCurrencyCode()} and for callers
     * that need to ask "what reference is in force?" without loading a row. The
     * single-reference invariant makes this well-defined when rows exist; the empty table
     * returns {@code null}.</p>
     */
    @Query("select r.referenceCurrencyCode from CurrencyRateEntity r "
            + "where r.id = (select min(r2.id) from CurrencyRateEntity r2)")
    String getReferenceCurrencyCode();
}