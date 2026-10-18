package com.salary.management.application.snapshot;

import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.CurrencyRateRepository;
import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Converts {@link Amounts} from a foreign currency to the system reference currency.
 *
 * <p>Loads the reference currency and every stored rate once, at construction. The
 * constructor does not fail when the reference currency is missing: with no reference
 * currency there is nothing to convert to, and the failure is raised per call by
 * {@link #toReference(Amounts, String)} so the caller can turn it into a report blocker
 * rather than a context-startup failure.</p>
 *
 * <p>Each of the four components is converted and rounded individually, then the reference
 * {@code total} is recomputed by {@code Amounts.builder()} as the sum of the four converted
 * components. That keeps {@code total = base + variable + allowances + employerTaxes} exact
 * on the reference side, matching the {@code ck_aggregate_reference_total} check.</p>
 */
@Component
@RequiredArgsConstructor
public class CurrencyConverter {

    public static final String REFERENCE_NULL = "referenceCurrency.null";
    public static final String RATE_NOT_FOUND = "currencyRate.not.found";

    private final CurrencyRateRepository currencyRateRepository;

    private String referenceCurrency;
    private Map<String, CurrencyRate> ratesByCurrency;

    /**
     * Loads the reference currency and the rate index. Called by Spring after construction.
     * Does not throw when the reference currency is missing; the failure surfaces in
     * {@link #toReference(Amounts, String)}.
     */
    public void initConverter() {
        this.referenceCurrency = currencyRateRepository.getReferenceCurrencyCode();
        List<CurrencyRate> rates = currencyRateRepository.list(
                CurrencyRateCriteria.builder().build());
        this.ratesByCurrency = rates.stream()
                .collect(Collectors.toMap(CurrencyRate::getCurrencyCode, r -> r));
    }

    /**
     * Converts {@code amounts} from {@code foreignCurrencyCode} to the reference currency.
     *
     * <p>A fresh {@code Amounts} is always returned, even when the input is already in the
     * reference currency; the input is never returned as-is, so callers can mutate the
     * result without affecting the original.</p>
     *
     * @throws IllegalArgumentException with message {@code "referenceCurrency.null"} when
     *                                  the system has no reference currency, or
     *                                  {@code "currencyRate.not.found"} when
     *                                  {@code foreignCurrencyCode} has no rate
     */
    public Amounts toReference(Amounts amounts, String foreignCurrencyCode) {
        if (referenceCurrency == null) {
            throw new IllegalArgumentException(REFERENCE_NULL);
        }
        if (referenceCurrency.equals(foreignCurrencyCode)) {
            return Amounts.builder()
                    .base(amounts.getBase())
                    .variable(amounts.getVariable())
                    .allowances(amounts.getAllowances())
                    .employerTaxes(amounts.getEmployerTaxes())
                    .currency(referenceCurrency)
                    .build();
        }
        CurrencyRate rate = ratesByCurrency.get(foreignCurrencyCode);
        if (rate == null) {
            throw new IllegalArgumentException(RATE_NOT_FOUND);
        }
        return Amounts.builder()
                .base(convert(amounts.getBase(), rate))
                .variable(convert(amounts.getVariable(), rate))
                .allowances(convert(amounts.getAllowances(), rate))
                .employerTaxes(convert(amounts.getEmployerTaxes(), rate))
                .currency(referenceCurrency)
                .build();
    }

    private BigDecimal convert(BigDecimal amount, CurrencyRate rate) {
        return rate.getReferenceAmount()
                .multiply(amount)
                .divide(rate.getRate(), Amounts.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}