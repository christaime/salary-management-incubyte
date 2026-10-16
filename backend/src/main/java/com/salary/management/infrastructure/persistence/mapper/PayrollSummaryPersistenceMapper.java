package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
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
     * Maps a domain payroll summary to its entity. The payroll period is stored as its UUID.
     *
     * @param summary source domain object
     * @return mapped entity
     */
    @Mapping(target = "payrollPeriodId", source = "payrollPeriod.id")
    PayrollSummaryEntity toEntity(PayrollSummary summary);

    /**
     * Maps a payroll summary entity to its domain object, resolving the period through
     * {@link PayrollPeriodPersistenceMapper}.
     *
     * @param entity        source entity
     * @param payrollPeriod resolved payroll period entity
     * @return mapped domain object
     */
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "employeeId", source = "entity.employeeId")
    @Mapping(target = "payrollPeriod", source = "payrollPeriod")
    @Mapping(target = "employeePeriodicAssignmentId", source = "entity.employeePeriodicAssignmentId")
    @Mapping(target = "amounts", source = "entity.amounts")
    @Mapping(target = "status", source = "entity.status")
    PayrollSummary toDomain(PayrollSummaryEntity entity, PayrollPeriodEntity payrollPeriod);
}