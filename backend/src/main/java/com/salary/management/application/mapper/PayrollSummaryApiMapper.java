package com.salary.management.application.mapper;

import com.salary.management.application.dto.AmountsDto;
import com.salary.management.application.dto.RefDto;
import com.salary.management.application.dto.employee.EmployeeRefDto;
import com.salary.management.application.dto.payroll.*;
import com.salary.management.domain.model.*;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.values.Amounts;
import jakarta.validation.constraints.NotNull;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/** Maps payroll domain objects to their API DTOs. */
@Mapper(componentModel = "spring")
public interface PayrollSummaryApiMapper {

    @Mapping(target = "id", source = "summary.id")
    @Mapping(target = "status", source = "summary.status")
    @Mapping(target = "employee", source = "employee", qualifiedByName = "toEmployeeRef")
    @Mapping(target = "period", source = "summary.payrollPeriod", qualifiedByName = "toPeriodDto")
    @Mapping(target = "country", source = "country", qualifiedByName = "countryToRef")
    @Mapping(target = "department", source = "department", qualifiedByName = "departmentToRef")
    @Mapping(target = "role", source = "role", qualifiedByName = "roleToRef")
    @Mapping(target = "employmentType", source = "employmentType")
    @Mapping(target = "assignment", source = "assignment", qualifiedByName = "toPayrollAssignmentDto")
    @Mapping(target = "amounts", source = "summary.amounts", qualifiedByName = "toAmountsDto")
    PayrollSummaryDto toDto(
            PayrollSummary summary,
            Employee employee,
            Country country,
            Department department,
            Role role,
            EmployeePeriodicAssignment assignment,
            EmploymentType employmentType);

    @Mapping(target = "id", source = "summary.id")
    @Mapping(target = "status", source = "summary.status")
    @Mapping(target = "employee", source = "employee", qualifiedByName = "toEmployeeRef")
    @Mapping(target = "period", source = "summary.payrollPeriod", qualifiedByName = "toPeriodDto")
    @Mapping(target = "country", source = "country", qualifiedByName = "countryToRef")
    @Mapping(target = "department", source = "department", qualifiedByName = "departmentToRef")
    @Mapping(target = "role", source = "role", qualifiedByName = "roleToRef")
    @Mapping(target = "employmentType", source = "assignment.employmentType")
    @Mapping(target = "amounts", source = "summary.amounts", qualifiedByName = "toAmountsDto")
    @Mapping(target = "assignment", source = "assignment", qualifiedByName = "toAssignmentDto")
    PayrollSummaryDetailDto toDetail(
            PayrollSummary summary,
            Employee employee,
            Country country,
            Department department,
            Role role,
            EmployeePeriodicAssignment assignment);

    // ------------------------------------------------------------------
    // Named helpers
    // ------------------------------------------------------------------

    @Named("toEmployeeRef")
    default EmployeeRefDto toEmployeeRef(Employee e) {
        return e == null ? null : new EmployeeRefDto(e.getId(), e.getMatricule(), e.getName());
    }

    @Named("toPeriodDto")
    default PayrollPeriodDto toPeriodDto(PayrollPeriod p) {
        if (p == null) return null;
        return PayrollPeriodDto.builder()
                .id(p.getId().toString())
                .name(p.getName())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .frequency(p.getFrequency())
                .build();
    }

    @Named("toAmountsDto")
    default AmountsDto toAmountsDto(Amounts a) {
        if (a == null) return null;
        return AmountsDto.builder()
                .base(a.getBase())
                .variable(a.getVariable())
                .allowances(a.getAllowances())
                .employerTaxes(a.getEmployerTaxes())
                .total(a.getTotal())
                .currency(a.getCurrency())
                .build();
    }

    @Named("toAssignmentDto")
    default PeriodicAssignmentDto toAssignmentDto(EmployeePeriodicAssignment a) {
        if (a == null) return null;
        return PeriodicAssignmentDto.builder()
                .id(a.getId())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .hasPayroll(true)             // populated only on the detail path
                .build();
    }

    @Named("toPayrollAssignmentDto")
    default PayrollAssignmentDto assignmentToDto(EmployeePeriodicAssignment a) {
        if (a == null) return null;
        return PayrollAssignmentDto.builder()
                .id(a.getId().toString())
                .startDate(a.getStartDate())
                .endDate(a.getEndDate())
                .frequency(a.getPayrollFrequency())
                .build();
    }

    @Named("countryToRef")
    default RefDto countryToRef(Country c) {
        return c == null ? null : new RefDto(c.getId(), c.getIsoCode(), c.getName());
    }

    @Named("departmentToRef")
    default RefDto departmentToRef(Department d) {
        return d == null ? null : new RefDto(d.getId(), d.getCode(), d.getName());
    }

    @Named("roleToRef")
    default RefDto roleToRef(Role r) {
        return r == null ? null : new RefDto(r.getId(), r.getCode(), r.getName());
    }
}