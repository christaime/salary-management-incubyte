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
) {
    @Override
    public String toString() {
        return "PositionContextDto{" +
                "employeeCount=" + employeeCount +
                ", min=" + min +
                ", p10=" + p10 +
                ", p25=" + p25 +
                ", p50=" + p50 +
                ", p75=" + p75 +
                ", p90=" + p90 +
                ", max=" + max +
                '}';
    }
}