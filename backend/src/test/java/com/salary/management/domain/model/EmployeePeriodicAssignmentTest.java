package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmployeePeriodicAssignmentTest {

    private static Country aCountry() {
        return Country.builder().isoCode("FR").name("France").currencyCode("EUR").build();
    }

    private static Department aDepartment() {
        return Department.builder().code("ENG").name("Engineering").build();
    }

    private static Role aRole() {
        return Role.builder().code("SWE").name("Engineer").build();
    }

    private static EmployeePeriodicAssignment valid() {
        return EmployeePeriodicAssignment.builder()
                .employeeId(UUID.randomUUID())
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 12, 31))
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY)
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
        assertEquals("FR", assignment.getCountry().getIsoCode());
        assertEquals("Engineering", assignment.getDepartment().getName());
        assertEquals("Engineer", assignment.getRole().getName());
    }

    @Test
    @DisplayName("endDate is optional")
    void endDateOptional() {
        EmployeePeriodicAssignment assignment = EmployeePeriodicAssignment.builder()
                .employeeId(UUID.randomUUID())
                .startDate(LocalDate.of(2024, 1, 1))
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY)
                .employmentType(EmploymentType.CONTRACTOR)
                .inEffect(true)
                .build();
        assertNotNull(assignment.getId());
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
                        .country(aCountry())
                        .department(aDepartment())
                        .role(aRole())
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
                        .country(aCountry())
                        .department(aDepartment())
                        .role(aRole())
                        .employmentType(EmploymentType.FULL_TIME)
                        .inEffect(true)
                        .build());
    }

    @Test
    @DisplayName("Missing country is rejected")
    void missingCountryRejected() {
        assertThrows(DomainValidationException.class,
                () -> EmployeePeriodicAssignment.builder()
                        .employeeId(UUID.randomUUID())
                        .startDate(LocalDate.of(2024, 1, 1))
                        .department(aDepartment())
                        .role(aRole())
                        .employmentType(EmploymentType.FULL_TIME)
                        .inEffect(true)
                        .build());
    }

    @Test
    @DisplayName("Missing department is rejected")
    void missingDepartmentRejected() {
        assertThrows(DomainValidationException.class,
                () -> EmployeePeriodicAssignment.builder()
                        .employeeId(UUID.randomUUID())
                        .startDate(LocalDate.of(2024, 1, 1))
                        .country(aCountry())
                        .role(aRole())
                        .employmentType(EmploymentType.FULL_TIME)
                        .inEffect(true)
                        .build());
    }

    @Test
    @DisplayName("Missing role is rejected")
    void missingRoleRejected() {
        assertThrows(DomainValidationException.class,
                () -> EmployeePeriodicAssignment.builder()
                        .employeeId(UUID.randomUUID())
                        .startDate(LocalDate.of(2024, 1, 1))
                        .country(aCountry())
                        .department(aDepartment())
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
                        .country(aCountry())
                        .department(aDepartment())
                        .role(aRole())
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

    @Test
    @DisplayName("withRole returns a new instance with updated role")
    void withRoleTransition() {
        EmployeePeriodicAssignment original = valid();
        Role newRole = Role.builder().code("SSWE").name("Senior Engineer").build();

        EmployeePeriodicAssignment updated = original.withRole(newRole);

        assertNotSame(original, updated);
        assertEquals("Engineer", original.getRole().getName());
        assertEquals("Senior Engineer", updated.getRole().getName());
    }
}