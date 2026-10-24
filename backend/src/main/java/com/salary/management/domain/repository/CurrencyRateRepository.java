package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link CurrencyRate}.
 *
 * <p>Rates are replaced, never appended to. {@link #saveAll(List)} deletes every stored row
 * and inserts the given batch, all within one transaction, so the table always reflects
 * exactly the last successful {@code saveAll}. The batch must share one
 * {@code referenceCurrencyCode}; the empty table and the post-save table both satisfy the
 * single-reference invariant. Direct SQL writers (Flyway seeds, tests, admin tools) are
 * also guarded by a database trigger on {@code currency_rates}.</p>
 */
public interface CurrencyRateRepository {

    /**
     * Replaces every stored rate with the given batch, atomically. Rejects an empty batch
     * and a batch that mixes reference currencies; writes nothing in either case.
     */
    List<CurrencyRate> saveAll(List<CurrencyRate> currencyRates);

    /** Deletes every stored rate. */
    void deleteAll();

    /**
     * Reference currency shared by every stored row, or {@code null} when the table is
     * empty. The single-reference invariant guarantees a single value when rows exist.
     */
    String getReferenceCurrencyCode();

    Optional<CurrencyRate> findById(UUID id);

    Optional<CurrencyRate> findByCurrencyCodeAndReferenceCurrencyCode(
            String currencyCode, String referenceCurrencyCode);

    List<CurrencyRate> list(CurrencyRateCriteria criteria);

    DataPage<CurrencyRate> page(CurrencyRateCriteria criteria);

    void deleteById(UUID id);
}