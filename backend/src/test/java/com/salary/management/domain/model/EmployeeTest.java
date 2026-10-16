package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeeTest {

    private static Country aCountry() {
        return Country.builder()
                .isoCode("FR")
                .name("France")
                .currencyCode("EUR")
                .build();
    }

    private static Employee valid() {
        return Employee.builder()
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada.lovelace@example.com")
                .birthDate(LocalDate.of(1990, 5, 12))
                .originCountry(aCountry())
                .build();
    }

    @Test
    @DisplayName("Valid employee is constructed and id is generated")
    void validConstruction() {
        Employee employee = valid();
        assertNotNull(employee.getId());
        assertEquals("EMP-0001", employee.getMatricule());
        assertEquals("Ada Lovelace", employee.getName());
        assertEquals("ada.lovelace@example.com", employee.getEmail());
        assertEquals(LocalDate.of(1990, 5, 12), employee.getBirthDate());
        assertEquals("FR", employee.getOriginCountry().getIsoCode());
    }

    @Test
    @DisplayName("originCountry is nullable")
    void originCountryNullable() {
        Employee employee = Employee.builder()
                .matricule("EMP-0002")
                .name("Alan Turing")
                .email("alan.turing@example.com")
                .birthDate(LocalDate.of(1912, 6, 23))
                .build();
        assertNotNull(employee.getId());
        assertNull(employee.getOriginCountry());
    }

    @Test
    @DisplayName("Provided id is preserved")
    void providedIdPreserved() {
        UUID id = UUID.randomUUID();
        Employee employee = Employee.builder()
                .id(id)
                .matricule("EMP-0002")
                .name("Alan Turing")
                .email("alan.turing@example.com")
                .birthDate(LocalDate.of(1912, 6, 23))
                .build();
        assertEquals(id, employee.getId());
    }

    @Test
    @DisplayName("Blank matricule is rejected")
    void blankMatriculeRejected() {
        assertThrows(DomainValidationException.class, () -> Employee.builder()
                .matricule(" ")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .birthDate(LocalDate.of(1990, 5, 12))
                .build());
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(DomainValidationException.class, () -> Employee.builder()
                .matricule("EMP-0001")
                .name(" ")
                .email("ada@example.com")
                .birthDate(LocalDate.of(1990, 5, 12))
                .build());
    }

    @Test
    @DisplayName("Invalid email is rejected")
    void invalidEmailRejected() {
        assertThrows(DomainValidationException.class, () -> Employee.builder()
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("not-an-email")
                .birthDate(LocalDate.of(1990, 5, 12))
                .build());
    }

    @Test
    @DisplayName("Null birthDate is rejected")
    void nullBirthDateRejected() {
        assertThrows(DomainValidationException.class, () -> Employee.builder()
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build());
    }

    @Test
    @DisplayName("withName returns a new instance")
    void withNameTransition() {
        Employee original = valid();
        Employee updated = original.withName("Ada King");

        assertNotSame(original, updated);
        assertEquals("Ada Lovelace", original.getName());
        assertEquals("Ada King", updated.getName());
    }

    @Test
    @DisplayName("withName rejects blank input")
    void withNameRejectsInvalid() {
        assertThrows(DomainValidationException.class, () -> valid().withName(" "));
    }

    @Test
    @DisplayName("withEmail rejects invalid input")
    void withEmailRejectsInvalid() {
        assertThrows(DomainValidationException.class,
                () -> valid().withEmail("not-an-email"));
    }

    @Test
    @DisplayName("withOriginCountry returns a new instance")
    void withOriginCountryTransition() {
        Employee original = valid();
        Country newCountry = Country.builder()
                .isoCode("US")
                .name("United States")
                .currencyCode("USD")
                .build();

        Employee updated = original.withOriginCountry(newCountry);

        assertNotSame(original, updated);
        assertEquals("FR", original.getOriginCountry().getIsoCode());
        assertEquals("US", updated.getOriginCountry().getIsoCode());
    }
}