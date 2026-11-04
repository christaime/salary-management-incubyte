package com.salary.management.application.dto.period;

import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

import java.time.LocalDate;

/**
 * Request body for creating a single payroll period.
 *
 * <p>{@code name} is required; the client (or its form) is responsible for
 * suggesting a value. The backend does not auto-generate names.</p>
 */
@Builder
public record PayrollPeriodCreateRequest(
        @NotBlank(message = "name is required")
        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        String name,

        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate,

        @NotNull(message = "frequency is required")
        PayrollPeriodFrequency frequency
) {}