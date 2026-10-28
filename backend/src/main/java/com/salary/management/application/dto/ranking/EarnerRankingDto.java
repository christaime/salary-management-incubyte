package com.salary.management.application.dto.ranking;

import com.salary.management.application.dto.employee.EmployeeRefDto;
import com.salary.management.application.dto.PayrollPeriodResponse;
import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record EarnerRankingDto(
        BigDecimal monthlyReferenceAmount,
        PayrollPeriodFrequency frequency,
        Integer percentile,                 // 9966 => P99.66 ; 4 => P0.04 ; null if n < 10
        Integer rank,
        EmployeeRefDto employee,
        RefDto role,
        RefDto department,
        RefDto country,
        EmploymentType employmentType,
        PayrollPeriodResponse period // The period on witch the employee earn the pay earning him his rank
) {}