package com.salary.management.application.mapper;

import com.salary.management.application.dto.PayrollPeriodResponse;
import com.salary.management.domain.model.PayrollPeriod;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PayrollPeriodApiMapper {

    PayrollPeriodResponse toResponse(PayrollPeriod payrollPeriod);
}