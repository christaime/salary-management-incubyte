package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for {@link Employee}. */
class EmployeeTest {

    @Test
    @DisplayName("valid build succeeds and generates an id")
    void validBuildSucceedsAndGeneratesId() {
        Employee employee = valid().build();

        assertNotNull(employee.getId());
        assertEquals("EMP-0001", employee.getMatricule());
        assertEquals("Ada Lovelace", employee.getName());
    }

    @Test
    @DisplayName("null matricule is rejected")
    void nullMatriculeIsRejected() {
        assertThrows(DomainValidationException.class, () -> valid().matricule(null).build());
    }

    @Test
    @DisplayName("blank matricule is rejected")
    void blankMatriculeIsRejected() {
        assertThrows(DomainValidationException.class, () -> valid().matricule("   ").build());
    }

    @Test
    @DisplayName("null name is rejected")
    void nullNameIsRejected() {
        assertThrows(DomainValidationException.class, () -> valid().name(null).build());
    }

    @Test
    @DisplayName("blank name is rejected")
    void blankNameIsRejected() {
        assertThrows(DomainValidationException.class, () -> valid().name("   ").build());
    }

    @Test
    @DisplayName("null email is rejected")
    void nullEmailIsRejected() {
        assertThrows(DomainValidationException.class, () -> valid().email(null).build());
    }

    @Test
    @DisplayName("blank email is rejected")
    void blankEmailIsRejected() {
        assertThrows(DomainValidationException.class, () -> valid().email("   ").build());
    }

    @Test
    @DisplayName("malformed email is rejected")
    void malformedEmailIsRejected() {
        assertThrows(DomainValidationException.class, () -> valid().email("not-an-email").build());
    }

    @Test
    @DisplayName("null birthDate is accepted")
    void nullBirthDateIsAccepted() {
        Employee employee = valid().birthDate(null).build();

        assertNull(employee.getBirthDate());
    }

    @Test
    @DisplayName("future birthDate is rejected")
    void futureBirthDateIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().birthDate(LocalDate.now().plusDays(1)).build());
    }

    @Test
    @DisplayName("today birthDate is rejected")
    void todayBirthDateIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().birthDate(LocalDate.now().plusDays(1)).build());
    }

    @Test
    @DisplayName("null originCountry is accepted")
    void nullOriginCountryIsAccepted() {
        Employee employee = valid().originCountry(null).build();

        assertNull(employee.getOriginCountry());
    }

    @Test
    @DisplayName("withName returns a new instance with the same id")
    void withNameReturnsNewInstanceWithSameId() {
        Employee original = valid().build();
        Employee updated = original.withName("Grace Hopper");

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals("Ada Lovelace", original.getName());
        assertEquals("Grace Hopper", updated.getName());
    }

    @Test
    @DisplayName("withEmail returns a new instance with the same id")
    void withEmailReturnsNewInstanceWithSameId() {
        Employee original = valid().build();
        Employee updated = original.withEmail("grace@example.com");

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals("ada@example.com", original.getEmail());
        assertEquals("grace@example.com", updated.getEmail());
    }

    @Test
    @DisplayName("withOriginCountry returns a new instance with the same id")
    void withOriginCountryReturnsNewInstanceWithSameId() {
        Employee original = valid().originCountry(null).build();
        Employee updated = original.withOriginCountry(aCountry());

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertNull(original.getOriginCountry());
        assertEquals("FR", updated.getOriginCountry().getIsoCode());
    }

    @Test
    @DisplayName("happy path with realistic values")
    void happyPathWithRealisticValues() {
        Employee employee = valid()
                .birthDate(LocalDate.of(1815, 12, 10))
                .originCountry(aCountry())
                .build();

        assertEquals("EMP-0001", employee.getMatricule());
        assertEquals("Ada Lovelace", employee.getName());
        assertEquals("ada@example.com", employee.getEmail());
        assertEquals(LocalDate.of(1815, 12, 10), employee.getBirthDate());
        assertEquals("FR", employee.getOriginCountry().getIsoCode());
    }

    private Employee.Builder valid() {
        return Employee.builder()
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com");
    }

    private Country aCountry() {
        return Country.builder()
                .isoCode("FR")
                .name("France")
                .currencyCode("EUR")
                .build();
    }
}