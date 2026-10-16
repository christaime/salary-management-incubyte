package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps between {@link PayrollPeriodEarnerRanking} and {@link PayrollPeriodEarnerRankingEntity}. */
@Mapper(
        componentModel = "spring",
        uses = {
                PayrollPeriodPersistenceMapper.class,
                CountryPersistenceMapper.class,
                DepartmentPersistenceMapper.class,
                RolePersistenceMapper.class,
                EmployeePersistenceMapper.class
        }
)
public interface PayrollPeriodEarnerRankingPersistenceMapper {

    /**
     * Maps a domain ranking entry to its entity. Foreign references are stored as UUIDs.
     *
     * @param ranking source domain object
     * @return mapped entity
     */
    @Mapping(target = "payrollPeriodId", source = "payrollPeriod.id")
    @Mapping(target = "countryId", source = "country.id")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "employeeId", source = "employee.id")
    PayrollPeriodEarnerRankingEntity toEntity(PayrollPeriodEarnerRanking ranking);

    /**
     * Maps a ranking entity to its domain object, resolving all foreign references through
     * their respective mappers.
     *
     * @param entity        source entity
     * @param payrollPeriod resolved payroll period entity
     * @param country       resolved country entity
     * @param department    resolved department entity
     * @param role          resolved role entity
     * @param employee      resolved employee entity
     * @return mapped domain object
     */
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "payrollPeriod", source = "payrollPeriod")
    @Mapping(target = "status", source = "entity.status")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "employmentType", source = "entity.employmentType")
    @Mapping(target = "direction", source = "entity.direction")
    @Mapping(target = "rank", source = "entity.rank")
    @Mapping(target = "employee", source = "employee")
    @Mapping(target = "employee.originCountry", ignore = true)
    @Mapping(target = "amount", source = "entity.amount")
    PayrollPeriodEarnerRanking toDomain(
            PayrollPeriodEarnerRankingEntity entity,
            PayrollPeriodEntity payrollPeriod,
            CountryEntity country,
            DepartmentEntity department,
            RoleEntity role,
            EmployeeEntity employee
    );
}