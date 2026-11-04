package com.salary.management.application.mapper;

import com.salary.management.application.dto.period.PayrollPeriodResponse;
import com.salary.management.domain.model.PayrollPeriod;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PayrollPeriodApiMapper {

    @Mapping(target = "referenced", ignore = true)
    PayrollPeriodResponse toResponse(PayrollPeriod payrollPeriod);
}