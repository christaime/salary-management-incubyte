package com.salary.management.application.dto.ranking;

import java.math.BigDecimal;

public record PositionContextDto(
        int employeeCount,
        BigDecimal min,
        BigDecimal p10,
        BigDecimal p25,
        BigDecimal p50,
        BigDecimal p75,
        BigDecimal p90,
        BigDecimal max
) {}