package com.salary.management.application.dto.employee;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record EmployeeAssignmentToCloseRequest(
        @NotNull UUID id,
        @NotNull LocalDate endDate
){}
