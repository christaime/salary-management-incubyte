package com.salary.management.application.mapper;

import com.salary.management.application.dto.RefDto;
import com.salary.management.application.dto.employee.EmployeeAssignmentDto;
import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.model.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface EmployeeApiMapper {

    @Mapping(target = "id", source = "employee.id")
    @Mapping(target = "matricule", source = "employee.matricule")
    @Mapping(target = "name", source = "employee.name")
    @Mapping(target = "email", source = "employee.email")
    @Mapping(target = "birthDate", source = "employee.birthDate")
    @Mapping(target = "originCountry", source = "employee.originCountry", qualifiedByName = "countryToRef")
    @Mapping(target = "assignmentInEffect", source = "assignment", qualifiedByName = "assignmentToDto")
    EmployeeWithInEffectAssignmentDto toDto( Employee employee, EmployeePeriodicAssignment assignment);

    @Named("assignmentToDto")
    @Mapping(target = "country", source = "country", qualifiedByName = "countryToRef")
    @Mapping(target = "department", source = "department", qualifiedByName = "departmentToRef")
    @Mapping(target = "role", source = "role", qualifiedByName = "roleToRef")
    @Mapping(target = "hasPayroll", ignore = true)
    EmployeeAssignmentDto toAssignmentDto(EmployeePeriodicAssignment assignment);

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