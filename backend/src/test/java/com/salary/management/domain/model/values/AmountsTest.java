package com.salary.management.domain.model.values;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Plain JUnit 5 tests for the {@link Amounts} value object.
 *
 * <p>No Spring, no mocks — just the domain class and its builder.</p>
 */
class AmountsTest {

    private static Amounts validAmounts() {
        return Amounts.builder()
                .base(new BigDecimal("1000"))
                .variable(new BigDecimal("200"))
                .allowances(new BigDecimal("50"))
                .employerTaxes(new BigDecimal("150"))
                .currency("USD")
                .build();
    }

    @Test
    @DisplayName("Valid construction succeeds and computes total from components")
    void validConstructionSucceedsAndComputesTotal() {
        Amounts amounts = validAmounts();

        assertEquals(new BigDecimal("1000"), amounts.getBase());
        assertEquals(new BigDecimal("200"), amounts.getVariable());
        assertEquals(new BigDecimal("50"), amounts.getAllowances());
        assertEquals(new BigDecimal("150"), amounts.getEmployerTaxes());
        assertEquals(new BigDecimal("1400"), amounts.getTotal());
        assertEquals("USD", amounts.getCurrency());
    }

    @Test
    @DisplayName("Explicit total matching the computed sum is accepted")
    void explicitMatchingTotalIsAccepted() {
        Amounts amounts = Amounts.builder()
                .base(new BigDecimal("1000"))
                .variable(new BigDecimal("200"))
                .allowances(new BigDecimal("50"))
                .employerTaxes(new BigDecimal("150"))
                .total(new BigDecimal("1400"))
                .currency("USD")
                .build();

        assertEquals(new BigDecimal("1400"), amounts.getTotal());
    }

    @Test
    @DisplayName("Explicit total differing from the computed sum is rejected")
    void explicitMismatchedTotalIsRejected() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                Amounts.builder()
                        .base(new BigDecimal("1000"))
                        .variable(new BigDecimal("200"))
                        .allowances(new BigDecimal("50"))
                        .employerTaxes(new BigDecimal("150"))
                        .total(new BigDecimal("9999"))
                        .currency("USD")
                        .build());

        assertEquals(
                "Validation failed: total must equal the sum of base, variable, allowances and employerTaxes",
                ex.getMessage());
    }

    @Test
    @DisplayName("Omitted components default to zero and total equals base")
    void missingComponentsDefaultToZero() {
        Amounts amounts = Amounts.builder()
                .base(new BigDecimal("500"))
                .currency("EUR")
                .build();

        assertEquals(BigDecimal.ZERO, amounts.getVariable());
        assertEquals(BigDecimal.ZERO, amounts.getAllowances());
        assertEquals(BigDecimal.ZERO, amounts.getEmployerTaxes());
        assertEquals(new BigDecimal("500"), amounts.getTotal());
    }

    @Test
    @DisplayName("Negative base amount is rejected")
    void negativeAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                Amounts.builder()
                        .base(new BigDecimal("-1"))
                        .currency("USD")
                        .build());
    }

    @Test
    @DisplayName("Blank currency is rejected")
    void blankCurrencyIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                Amounts.builder()
                        .base(new BigDecimal("10"))
                        .currency("  ")
                        .build());
    }

    @Test
    @DisplayName("Null currency is rejected")
    void nullCurrencyIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                Amounts.builder()
                        .base(new BigDecimal("10"))
                        .currency(null)
                        .build());
    }

    @Test
    @DisplayName("Currency must be exactly three characters")
    void currencyMustBeExactlyThreeChars() {
        assertThrows(IllegalArgumentException.class, () ->
                Amounts.builder()
                        .base(new BigDecimal("10"))
                        .currency("US")
                        .build());

        assertThrows(IllegalArgumentException.class, () ->
                Amounts.builder()
                        .base(new BigDecimal("10"))
                        .currency("USDX")
                        .build());
    }

    @Test
    @DisplayName("withBase returns a new instance and recomputes the total")
    void withBaseReturnsNewInstanceAndRecomputesTotal() {
        Amounts original = validAmounts();

        Amounts updated = original.withBase(new BigDecimal("2000"));

        assertNotSame(original, updated);
        assertEquals(new BigDecimal("1000"), original.getBase());
        assertEquals(new BigDecimal("2000"), updated.getBase());
        assertEquals(new BigDecimal("2400"), updated.getTotal());
    }

    @Test
    @DisplayName("withVariable returns a new instance and recomputes the total")
    void withVariableReturnsNewInstanceAndRecomputesTotal() {
        Amounts original = validAmounts();

        Amounts updated = original.withVariable(new BigDecimal("400"));

        assertNotSame(original, updated);
        assertEquals(new BigDecimal("200"), original.getVariable());
        assertEquals(new BigDecimal("400"), updated.getVariable());
        assertEquals(new BigDecimal("1600"), updated.getTotal());
    }

    @Test
    @DisplayName("withAllowances returns a new instance and recomputes the total")
    void withAllowancesReturnsNewInstanceAndRecomputesTotal() {
        Amounts original = validAmounts();

        Amounts updated = original.withAllowances(new BigDecimal("75"));

        assertNotSame(original, updated);
        assertEquals(new BigDecimal("50"), original.getAllowances());
        assertEquals(new BigDecimal("75"), updated.getAllowances());
        assertEquals(new BigDecimal("1425"), updated.getTotal());
    }

    @Test
    @DisplayName("withEmployerTaxes returns a new instance and recomputes the total")
    void withEmployerTaxesReturnsNewInstanceAndRecomputesTotal() {
        Amounts original = validAmounts();

        Amounts updated = original.withEmployerTaxes(new BigDecimal("300"));

        assertNotSame(original, updated);
        assertEquals(new BigDecimal("150"), original.getEmployerTaxes());
        assertEquals(new BigDecimal("300"), updated.getEmployerTaxes());
        assertEquals(new BigDecimal("1550"), updated.getTotal());
    }

    @Test
    @DisplayName("withCurrency returns a new instance with the updated currency")
    void withCurrencyReturnsNewInstance() {
        Amounts original = validAmounts();

        Amounts updated = original.withCurrency("EUR");

        assertNotSame(original, updated);
        assertEquals("USD", original.getCurrency());
        assertEquals("EUR", updated.getCurrency());
    }

    @Test
    @DisplayName("withBase rejects a negative amount")
    void withBaseRejectsInvalidInput() {
        Amounts original = validAmounts();

        assertThrows(IllegalArgumentException.class,
                () -> original.withBase(new BigDecimal("-5")));
    }

    @Test
    @DisplayName("withCurrency rejects a non three-character currency")
    void withCurrencyRejectsInvalidInput() {
        Amounts original = validAmounts();

        assertThrows(IllegalArgumentException.class,
                () -> original.withCurrency("US"));
    }
}