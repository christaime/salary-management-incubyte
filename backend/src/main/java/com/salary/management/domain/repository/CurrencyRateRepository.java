package com.salary.management.domain.repository;

import com.salary.management.domain.model.CurrencyRate;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link CurrencyRate} reference data.
 */
public interface CurrencyRateRepository {

    CurrencyRate save(CurrencyRate currencyRate);

    Optional<CurrencyRate> findByCurrencyCodeAndReferenceCurrencyCode(
            String currencyCode, String referenceCurrencyCode);

    List<CurrencyRate> findAll();
}