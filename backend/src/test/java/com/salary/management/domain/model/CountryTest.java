package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountryTest {

    private static Country valid() {
        return Country.builder()
                .isoCode("FR")
                .name("France")
                .currencyCode("EUR")
                .build();
    }

    @Test
    @DisplayName("Valid country is constructed and id is generated")
    void validConstruction() {
        Country country = valid();
        assertNotNull(country.getId());
        assertEquals("FR", country.getIsoCode());
        assertEquals("France", country.getName());
        assertEquals("EUR", country.getCurrencyCode());
    }

    @Test
    @DisplayName("Provided id is preserved")
    void providedIdPreserved() {
        UUID id = UUID.randomUUID();
        Country country = Country.builder()
                .id(id).isoCode("FR").name("France").currencyCode("EUR").build();
        assertEquals(id, country.getId());
    }

    @Test
    @DisplayName("Blank isoCode is rejected")
    void blankIsoCodeRejected() {
        assertThrows(DomainValidationException.class, () -> Country.builder()
                .isoCode(" ").name("France").currencyCode("EUR").build());
    }

    @Test
    @DisplayName("isoCode length must be 2 or 3")
    void isoCodeLengthEnforced() {
        assertThrows(DomainValidationException.class, () -> Country.builder()
                .isoCode("F").name("France").currencyCode("EUR").build());
        assertThrows(DomainValidationException.class, () -> Country.builder()
                .isoCode("FRAN").name("France").currencyCode("EUR").build());
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(DomainValidationException.class, () -> Country.builder()
                .isoCode("FR").name(" ").currencyCode("EUR").build());
    }

    @Test
    @DisplayName("Name length must be between 2 and 100")
    void nameLengthEnforced() {
        assertThrows(DomainValidationException.class,
                () -> Country.builder().name("A").build());
        assertThrows(DomainValidationException.class,
                () -> Country.builder().name("A".repeat(101)).build());
    }

    @Test
    @DisplayName("currencyCode must be exactly 3 chars")
    void currencyCodeLengthEnforced() {
        assertThrows(DomainValidationException.class, () -> Country.builder()
                .isoCode("FR").name("France").currencyCode("EU").build());
    }

    @Test
    @DisplayName("withName returns a new instance with updated name")
    void withNameTransition() {
        Country original = valid();
        Country updated = original.withName("Republique francaise");

        assertNotSame(original, updated);
        assertEquals("France", original.getName());
        assertEquals("Republique francaise", updated.getName());
    }

    @Test
    @DisplayName("withName rejects blank input")
    void withNameRejectsInvalid() {
        assertThrows(DomainValidationException.class, () -> valid().withName(" "));
    }
}