package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CurrencyRateTest {

    private static CurrencyRate valid() {
        return CurrencyRate.builder()
                .currencyCode("USD")
                .referenceCurrencyCode("EUR")
                .rate(new BigDecimal("0.92"))
                .build();
    }

    @Test
    @DisplayName("Valid currency rate is constructed and id is generated")
    void validConstruction() {
        CurrencyRate rate = valid();
        assertNotNull(rate.getId());
        assertEquals("USD", rate.getCurrencyCode());
        assertEquals("EUR", rate.getReferenceCurrencyCode());
        assertEquals(new BigDecimal("0.92"), rate.getRate());
    }

    @Test
    @DisplayName("Currency codes must be exactly 3 chars")
    void currencyCodesLengthEnforced() {
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("US").referenceCurrencyCode("EUR")
                .rate(BigDecimal.ONE).build());
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("EU")
                .rate(BigDecimal.ONE).build());
    }

    @Test
    @DisplayName("Null rate is rejected")
    void nullRateRejected() {
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("EUR").build());
    }

    @Test
    @DisplayName("Non-positive rate is rejected")
    void nonPositiveRateRejected() {
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("EUR")
                .rate(BigDecimal.ZERO).build());
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("EUR")
                .rate(new BigDecimal("-1")).build());
    }

    @Test
    @DisplayName("withRate returns a new instance")
    void withRateTransition() {
        CurrencyRate original = valid();
        CurrencyRate updated = original.withRate(new BigDecimal("0.95"));

        assertNotSame(original, updated);
        assertEquals(new BigDecimal("0.92"), original.getRate());
        assertEquals(new BigDecimal("0.95"), updated.getRate());
    }

    @Test
    @DisplayName("withRate rejects non-positive input")
    void withRateRejectsInvalid() {
        assertThrows(DomainValidationException.class,
                () -> valid().withRate(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("referenceAmount defaults to 1 when omitted")
    void referenceAmountDefaultsToOne() {
        CurrencyRate rate = valid();
        assertNotNull(rate.getReferenceAmount());
        assertEquals(0, rate.getReferenceAmount().compareTo(BigDecimal.ONE));
    }

    @Test
    @DisplayName("Explicit referenceAmount is preserved")
    void explicitReferenceAmountPreserved() {
        CurrencyRate rate = CurrencyRate.builder()
                .currencyCode("JPY")
                .referenceCurrencyCode("USD")
                .referenceAmount(new BigDecimal("100"))
                .rate(new BigDecimal("0.65"))
                .build();
        assertEquals(0, rate.getReferenceAmount().compareTo(new BigDecimal("100")));
    }

    @Test
    @DisplayName("Non-positive referenceAmount is rejected")
    void nonPositiveReferenceAmountRejected() {
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("EUR")
                .referenceAmount(BigDecimal.ZERO).rate(BigDecimal.ONE).build());
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("EUR")
                .referenceAmount(new BigDecimal("-1")).rate(BigDecimal.ONE).build());
    }

    @Test
    @DisplayName("currency and reference must differ")
    void sameCurrencyRejected() {
        assertThrows(DomainValidationException.class, () -> CurrencyRate.builder()
                .currencyCode("USD").referenceCurrencyCode("USD")
                .rate(BigDecimal.ONE).build());
    }
}