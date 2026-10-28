package com.salary.management.application.dto.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Write payload for creating or updating an employee.
 *
 * <p>{@code id} is null on create (the server generates it) and ignored on
 * update (the path parameter wins). {@code assignmentInEffect} is optional:
 * when null on update, the existing in-effect assignment is left untouched;
 * when present, it replaces the existing one.</p>
 */
@Builder
public record EmployeeSaveRequest(
        UUID id,                              // null on create, ignored on update
        @NotBlank @Size(max=100) String matricule,
        @NotBlank @Size(max=100) String name,
        @NotBlank @Size(max=254) @Email String email,
        @Past LocalDate birthDate,
        UUID originCountryId,                 // nullable
        @Valid EmployeeAssignmentSaveRequest assignmentInEffect,   // nullable
        @Valid EmployeeAssignmentToCloseRequest toClose// An assignment to close when promoting the current one to inEffect
) {}