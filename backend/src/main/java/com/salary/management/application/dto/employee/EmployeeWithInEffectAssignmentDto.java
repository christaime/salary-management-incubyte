package com.salary.management.application.dto.employee;

import com.salary.management.application.dto.RefDto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee paired with the assignment that was in effect for the
 * period the report covers.
 *
 * <p>Flattens the employee fields the report needs (id, matricule, name,
 * email, birth date, origin country) and adds the in-effect assignment.</p>
 */
@Builder
public record EmployeeWithInEffectAssignmentDto(
        UUID id,
        @NotBlank @Size(max=100) String matricule,
        @NotBlank @Size(max=100) String name,
        @NotBlank @Size(max=254) String email,
        @Past LocalDate birthDate,
        RefDto originCountry,
        EmployeeAssignmentDto assignmentInEffect
) {}