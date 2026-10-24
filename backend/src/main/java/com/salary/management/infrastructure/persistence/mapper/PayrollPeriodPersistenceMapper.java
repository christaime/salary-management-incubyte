package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import org.mapstruct.Mapper;

/** Maps between {@link PayrollPeriod} and {@link PayrollPeriodEntity}. */
@Mapper(componentModel = "spring")
public interface PayrollPeriodPersistenceMapper {

    /**
     * Maps a domain payroll period to its entity.
     *
     * @param payrollPeriod source domain object
     * @return mapped entity
     */
    PayrollPeriodEntity toEntity(PayrollPeriod payrollPeriod);

    /**
     * Maps a payroll period entity to its domain object.
     *
     * @param entity source entity
     * @return mapped domain object
     */
    PayrollPeriod toDomain(PayrollPeriodEntity entity);
}