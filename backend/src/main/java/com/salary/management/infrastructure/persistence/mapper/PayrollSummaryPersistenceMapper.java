package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps between {@link PayrollSummary} and {@link PayrollSummaryEntity}. */
@Mapper(
        componentModel = "spring",
        uses = {PayrollPeriodPersistenceMapper.class}
)
public interface PayrollSummaryPersistenceMapper {

    /**
     * Maps a domain payroll summary to its entity. The employee and assignment references are
     * resolved by the adapter and passed in; the four denormalized assignment columns are
     * copied from the assignment.
     *
     * @param summary    source domain object
     * @param employee   resolved employee entity
     * @param assignment resolved assignment entity
     * @return mapped entity
     */
    @Mapping(target = "employee", source = "employee")
    @Mapping(target = "payrollPeriod", source = "summary.payrollPeriod")
    @Mapping(target = "employeePeriodicAssignmentId", source = "summary.employeePeriodicAssignmentId")
    @Mapping(target = "assignmentCountryId", source = "assignment.countryId")
    @Mapping(target = "assignmentDepartmentId", source = "assignment.departmentId")
    @Mapping(target = "assignmentRoleId", source = "assignment.roleId")
    @Mapping(target = "assignmentEmploymentType", source = "assignment.employmentType")
    @Mapping(target = "id", source = "summary.id")
    @Mapping(target = "amounts", source = "summary.amounts")
    @Mapping(target = "status", source = "summary.status")
    PayrollSummaryEntity toEntity(
            PayrollSummary summary,
            EmployeeEntity employee,
            EmployeePeriodicAssignmentEntity assignment);

    /**
     * Maps a payroll summary entity to its domain object.
     *
     * @param entity source entity
     * @return mapped domain object
     */
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "employeeId", source = "entity.employee.id")
    @Mapping(target = "payrollPeriod", source = "entity.payrollPeriod")
    @Mapping(target = "employeePeriodicAssignmentId", source = "entity.employeePeriodicAssignmentId")
    @Mapping(target = "amounts", source = "entity.amounts")
    @Mapping(target = "status", source = "entity.status")
    PayrollSummary toDomain(PayrollSummaryEntity entity);
}