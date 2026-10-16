package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodAggregateEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps between {@link PayrollPeriodAggregate} and {@link PayrollPeriodAggregateEntity}. */
@Mapper(
        componentModel = "spring",
        uses = {
                PayrollPeriodPersistenceMapper.class,
                CountryPersistenceMapper.class,
                DepartmentPersistenceMapper.class,
                RolePersistenceMapper.class
        }
)
public interface PayrollPeriodAggregatePersistenceMapper {

    /**
     * Maps a domain aggregate to its entity. Foreign references are stored as UUIDs.
     *
     * @param aggregate source domain object
     * @return mapped entity
     */
    @Mapping(target = "payrollPeriodId", source = "payrollPeriod.id")
    @Mapping(target = "countryId", source = "country.id")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "roleId", source = "role.id")
    PayrollPeriodAggregateEntity toEntity(PayrollPeriodAggregate aggregate);

    /**
     * Maps an aggregate entity to its domain object, resolving all foreign references through
     * their respective mappers.
     *
     * @param entity        source entity
     * @param payrollPeriod resolved payroll period entity
     * @param country       resolved country entity
     * @param department    resolved department entity
     * @param role          resolved role entity
     * @return mapped domain object
     */
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "payrollPeriod", source = "payrollPeriod")
    @Mapping(target = "status", source = "entity.status")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "employmentType", source = "entity.employmentType")
    @Mapping(target = "originalAmounts", source = "entity.originalAmounts")
    @Mapping(target = "referenceAmounts", source = "entity.referenceAmounts")
    @Mapping(target = "employeeCount", source = "entity.employeeCount")
    PayrollPeriodAggregate toDomain(
            PayrollPeriodAggregateEntity entity,
            PayrollPeriodEntity payrollPeriod,
            CountryEntity country,
            DepartmentEntity department,
            RoleEntity role
    );
}