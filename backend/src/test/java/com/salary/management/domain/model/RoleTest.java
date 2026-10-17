package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleTest {

    private static Role valid() {
        return Role.builder().code("SWE").name("Engineer").build();
    }

    @Test
    @DisplayName("Valid role is constructed and id is generated")
    void validConstruction() {
        Role role = valid();
        assertNotNull(role.getId());
        assertEquals("SWE", role.getCode());
        assertEquals("Engineer", role.getName());
    }

    @Test
    @DisplayName("Blank name is rejected")
    void blankNameRejected() {
        assertThrows(DomainValidationException.class,
                () -> Role.builder().code("SWE").name(" ").build());
    }

    @Test
    @DisplayName("Name length must be between 2 and 100")
    void nameLengthEnforced() {
        assertThrows(DomainValidationException.class,
                () -> Role.builder().code("SWE").name("A").build());
        assertThrows(DomainValidationException.class,
                () -> Role.builder().code("SWE").name("A".repeat(101)).build());
    }

    @Test
    @DisplayName("withName returns a new instance")
    void withNameTransition() {
        Role original = valid();
        Role updated = original.withName("Senior Engineer");

        assertNotSame(original, updated);
        assertEquals("SWE", original.getCode());
        assertEquals("Engineer", original.getName());
        assertEquals("SWE", updated.getCode());
        assertEquals("Senior Engineer", updated.getName());
    }

    @Test
    @DisplayName("withName rejects invalid input")
    void withNameRejectsInvalid() {
        assertThrows(DomainValidationException.class, () -> valid().withName("A"));
    }

    @Test
    @DisplayName("Valid code is accepted")
    void validCodeAccepted() {
        Role role = Role.builder()
                .code("SWE")
                .name("Engineer")
                .build();

        assertEquals("SWE", role.getCode());
    }

    @Test
    @DisplayName("Null code is rejected")
    void nullCodeRejected() {
        assertThrows(DomainValidationException.class,
                () -> Role.builder().code(null).name("Engineer").build());
    }

    @Test
    @DisplayName("Blank code is rejected")
    void blankCodeRejected() {
        assertThrows(DomainValidationException.class,
                () -> Role.builder().code("   ").name("Engineer").build());
    }

    @Test
    @DisplayName("Code shorter than 2 is rejected")
    void codeTooShortRejected() {
        assertThrows(DomainValidationException.class,
                () -> Role.builder().code("S").name("Engineer").build());
    }

    @Test
    @DisplayName("Code longer than 10 is rejected")
    void codeTooLongRejected() {
        assertThrows(DomainValidationException.class,
                () -> Role.builder().code("ABCDEFGHIJK").name("Engineer").build());
    }
}