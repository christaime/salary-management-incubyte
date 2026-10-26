package com.salary.management.application.dto.ranking;

import com.salary.management.application.dto.DateRange;
import com.salary.management.application.dto.PayrollPeriodResponse;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.ReportKind;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record EarningRankingReportResponse(
        ReportKind kind,
        PayrollPeriodResponse period,
        String referenceCurrency,
        GroupKey groupKey,
        List<EarnerRankingDto> tops,
        List<EarnerRankingDto> bottoms,
        PositionContextDto positionContext,
        BigDecimal totalReferenceAmount,            // Global total for the date range
        BigDecimal totalDimensionReferenceAmount,   // Total for the specific dimension (e.g., total in Country X)
        Integer totalMonthlyPeriod,
        Integer totalBiWeeklyPeriod
) {}