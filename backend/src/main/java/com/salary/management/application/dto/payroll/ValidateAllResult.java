package com.salary.management.application.dto.payroll;

import java.util.List;
import java.util.UUID;

public record ValidateAllResult(
        int total,
        int validated,
        int failed,
        List<Failure> failures
) {
    public record Failure(UUID summaryId, String code, String message) {}
}