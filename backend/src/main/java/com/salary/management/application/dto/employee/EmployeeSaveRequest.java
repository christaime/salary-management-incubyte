package com.salary.management.application.dto.employee;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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
        @NotBlank String matricule,
        @NotBlank String name,
        @NotBlank @Email String email,
        LocalDate birthDate,                  // nullable
        UUID originCountryId,                 // nullable
        @Valid EmployeeAssignmentSaveRequest assignmentInEffect,   // nullable
        @Valid EmployeeAssignmentToCloseRequest toClose // An assignment to close when promoting the current one to inEffect
) {}