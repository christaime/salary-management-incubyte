package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.EmploymentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeePeriodicAssignmentTest {

    private static EmployeePeriodicAssignment valid() {
        return EmployeePeriodicAssignment.builder()
                .employeeId(UUID.randomUUID())
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .countryId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .employmentType(EmploymentType.FULL_TIME)
                .inEffect(true)
                .build();
    }

    @Test
    @DisplayName("Valid assignment is constructed and id is generated")
    void validConstruction() {
        EmployeePeriodicAssignment assignment = valid();
        assertNotNull(assignment.getId());
        assertEquals(LocalDate.of(2024, 1, 1), assignment.getStartDate());
        assertEquals(LocalDate.of(2024, 12, 31), assignment.getEndDate());
        assertEquals(EmploymentType.FULL_TIME, assignment.getEmploymentType());
    }

    @Test
    @DisplayName("endDate is optional")
    void endDateOptional() {
        EmployeePeriodicAssignment assignment = EmployeePeriodicAssignment.builder()
                .employeeId(UUID.randomUUID())
                .startDate(LocalDate.of(2024, 1, 1))
                .countryId(UUID.randomUUID())
                .departmentId(UUID.randomUUID())
                .roleId(UUID.randomUUID())
                .employmentType(EmploymentType.CONTRACTOR)
                .inEffect(true)
                .build();
        assertNotNull(assignment.getId());
        assertEquals(LocalDate.of(2024, 1, 1), assignment.getStartDate());
        assertEquals(null, assignment.getEndDate());
    }

    @Test
    @DisplayName("endDate must be after startDate")
    void endDateAfterStartDate() {
        assertThrows(DomainValidationException.class,
                () -> EmployeePeriodicAssignment.builder()
                        .employeeId(UUID.randomUUID())
                        .startDate(LocalDate.of(2024, 6, 1))
                        .endDate(LocalDate.of(2024, 6, 1))
                        .countryId(UUID.randomUUID())
                        .departmentId(UUID.randomUUID())
                        .roleId(UUID.randomUUID())
                        .employmentType(EmploymentType.FULL_TIME)
                        .inEffect(true)
                        .build());
    }

    @Test
    @DisplayName("Missing employeeId is rejected")
    void missingEmployeeIdRejected() {
        assertThrows(DomainValidationException.class,
                () -> EmployeePeriodicAssignment.builder()
                        .startDate(LocalDate.of(2024, 1, 1))
                        .countryId(UUID.randomUUID())
                        .departmentId(UUID.randomUUID())
                        .roleId(UUID.randomUUID())
                        .employmentType(EmploymentType.FULL_TIME)
                        .inEffect(true)
                        .build());
    }

    @Test
    @DisplayName("Missing employmentType is rejected")
    void missingEmploymentTypeRejected() {
        assertThrows(DomainValidationException.class,
                () -> EmployeePeriodicAssignment.builder()
                        .employeeId(UUID.randomUUID())
                        .startDate(LocalDate.of(2024, 1, 1))
                        .countryId(UUID.randomUUID())
                        .departmentId(UUID.randomUUID())
                        .roleId(UUID.randomUUID())
                        .inEffect(true)
                        .build());
    }

    @Test
    @DisplayName("withEndDate returns a new instance and re-validates")
    void withEndDateTransition() {
        EmployeePeriodicAssignment original = valid();
        EmployeePeriodicAssignment updated =
                original.withEndDate(LocalDate.of(2025, 6, 30));

        assertNotSame(original, updated);
        assertEquals(LocalDate.of(2024, 12, 31), original.getEndDate());
        assertEquals(LocalDate.of(2025, 6, 30), updated.getEndDate());
    }

    @Test
    @DisplayName("withEndDate rejects an endDate not after startDate")
    void withEndDateRejectsInvalid() {
        EmployeePeriodicAssignment original = valid();
        assertThrows(DomainValidationException.class,
                () -> original.withEndDate(LocalDate.of(2023, 12, 31)));
    }

    @Test
    @DisplayName("withInEffect returns a new instance")
    void withInEffectTransition() {
        EmployeePeriodicAssignment original = valid();
        EmployeePeriodicAssignment updated = original.withInEffect(false);

        assertNotSame(original, updated);
        assertEquals(true, original.getInEffect());
        assertEquals(false, updated.getInEffect());
    }
}