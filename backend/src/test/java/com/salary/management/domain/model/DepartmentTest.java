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
        return Department.builder().code("ENG").name("Engineering").build();
    }

    @Test
    @DisplayName("Valid department is constructed and id is generated")
    void validConstruction() {
        Department department = valid();
        assertNotNull(department.getId());
        assertEquals("ENG", department.getCode());
        assertEquals("Engineering", department.getName());
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().code("ENG").name(" ").build());
    }

    @Test
    @DisplayName("Name length must be between 2 and 100")
    void nameLengthEnforced() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().code("ENG").name("A").build());
        assertThrows(DomainValidationException.class,
                () -> Department.builder().code("ENG").name("A".repeat(101)).build());
    }

    @Test
    @DisplayName("withName returns a new instance")
    void withNameTransition() {
        Department original = valid();
        Department updated = original.withName("Platform");

        assertNotSame(original, updated);
        assertEquals("ENG", original.getCode());
        assertEquals("Engineering", original.getName());
        assertEquals("ENG", updated.getCode());
        assertEquals("Platform", updated.getName());
    }

    @Test
    @DisplayName("withName rejects invalid input")
    void withNameRejectsInvalid() {
        assertThrows(DomainValidationException.class, () -> valid().withName("A"));
    }

    @Test
    @DisplayName("Valid code is accepted")
    void validCodeAccepted() {
        Department department = Department.builder()
                .code("ENG")
                .name("Engineering")
                .build();

        assertEquals("ENG", department.getCode());
    }

    @Test
    @DisplayName("Null code is rejected")
    void nullCodeRejected() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().code(null).name("Engineering").build());
    }

    @Test
    @DisplayName("Blank code is rejected")
    void blankCodeRejected() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().code("   ").name("Engineering").build());
    }

    @Test
    @DisplayName("Code shorter than 2 is rejected")
    void codeTooShortRejected() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().code("E").name("Engineering").build());
    }

    @Test
    @DisplayName("Code longer than 10 is rejected")
    void codeTooLongRejected() {
        assertThrows(DomainValidationException.class,
                () -> Department.builder().code("ABCDEFGHIJK").name("Engineering").build());
    }
}