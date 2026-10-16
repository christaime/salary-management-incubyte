package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link CurrencyRate} reference data. */
public interface CurrencyRateRepository {

    CurrencyRate save(CurrencyRate currencyRate);

    Optional<CurrencyRate> findById(UUID id);

    Optional<CurrencyRate> findByCurrencyCodeAndReferenceCurrencyCode(
            String currencyCode, String referenceCurrencyCode);

    List<CurrencyRate> list(CurrencyRateCriteria criteria);

    DataPage<CurrencyRate> page(CurrencyRateCriteria criteria);

    void deleteById(UUID id);
}