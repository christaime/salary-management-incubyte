package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DepartmentTest {

    private static Department valid() {
        return Department.builder().name("Engineering").build();
    }

    @Test
    @DisplayName("Valid department is constructed and id is generated")
    void validConstruction() {
        Department department = valid();
        assertNotNull(department.getId());
        assertEquals("Engineering", department.getName());
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().name(" ").build());
    }

    @Test
    @DisplayName("Name length must be between 2 and 100")
    void nameLengthEnforced() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().name("A").build());
        assertThrows(DomainValidationException.class,
                () -> Department.builder().name("A".repeat(101)).build());
    }

    @Test
    @DisplayName("withName returns a new instance")
    void withNameTransition() {
        Department original = valid();
        Department updated = original.withName("Platform");

        assertNotSame(original, updated);
        assertEquals("Engineering", original.getName());
        assertEquals("Platform", updated.getName());
    }

    @Test
    @DisplayName("withName rejects invalid input")
    void withNameRejectsInvalid() {
        assertThrows(DomainValidationException.class, () -> valid().withName("A"));
    }
}