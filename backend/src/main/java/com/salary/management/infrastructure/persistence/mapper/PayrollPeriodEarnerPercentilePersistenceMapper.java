package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.PayrollPeriodEarnerPercentile;
import com.salary.management.domain.model.values.Amount;
import com.salary.management.infrastructure.persistence.embeddable.AmountEmbeddable;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerPercentileEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps between {@link PayrollPeriodEarnerPercentile} and {@link PayrollPeriodEarnerPercentileEntity}. */
@Mapper(
        componentModel = "spring",
        uses = {
                PayrollPeriodPersistenceMapper.class,
                CountryPersistenceMapper.class,
                DepartmentPersistenceMapper.class,
                RolePersistenceMapper.class
        }
)
public interface PayrollPeriodEarnerPercentilePersistenceMapper {

    /**
     * Maps a domain percentile entry to its entity. Foreign references are stored as UUIDs.
     * {@link Amount} maps to the {@code reference_amount} / {@code reference_currency} columns
     * via the flat fields on the entity (not an embeddable, unlike the ranking entity).
     *
     * @param percentile source domain object
     * @return mapped entity
     */
    @Mapping(target = "payrollPeriodId", source = "payrollPeriod.id")
    @Mapping(target = "countryId", source = "country.id")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "employmentType", source = "employmentType")
    @Mapping(target = "referenceAmount", source = "referenceAmount.amount")
    @Mapping(target = "referenceCurrency", source = "referenceAmount.currency")
    PayrollPeriodEarnerPercentileEntity toEntity(PayrollPeriodEarnerPercentile percentile);

    /**
     * Maps a percentile entity to its domain object, resolving all foreign references through
     * their respective mappers.
     *
     * @param entity        source entity
     * @param payrollPeriod resolved payroll period entity
     * @param country       resolved country entity ({@code null} for scoped-out dimensions)
     * @param department    resolved department entity ({@code null} for scoped-out dimensions)
     * @param role          resolved role entity ({@code null} for scoped-out dimensions)
     * @return mapped domain object
     */
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "payrollPeriod", source = "payrollPeriod")
    @Mapping(target = "status", source = "entity.status")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "employmentType", source = "entity.employmentType")
    @Mapping(target = "percentile", source = "entity.percentile")
    @Mapping(target = "referenceAmount", source = "entity")
    @Mapping(target = "employeeCount", source = "entity.employeeCount")
    PayrollPeriodEarnerPercentile toDomain(
            PayrollPeriodEarnerPercentileEntity entity,
            PayrollPeriodEntity payrollPeriod,
            CountryEntity country,
            DepartmentEntity department,
            RoleEntity role
    );

    /**
     * Helper for {@link #toDomain}: combines the flat {@code referenceAmount} and
     * {@code referenceCurrency} columns into a single domain {@link Amount}.
     *
     * <p>Declared as {@code default} so MapStruct wires it automatically for the
     * {@code referenceAmount} mapping above, without requiring an extra
     * {@code @Mapping} expression.</p>
     *
     * @param entity entity holding the two flat columns
     * @return combined domain amount, or {@code null} if either column is {@code null}
     */
    default Amount toAmount(PayrollPeriodEarnerPercentileEntity entity) {
        if (entity == null
                || entity.getReferenceAmount() == null
                || entity.getReferenceCurrency() == null) {
            return null;
        }
        return Amount.builder()
                .amount(entity.getReferenceAmount())
                .currency(entity.getReferenceCurrency())
                .build();
    }

    /**
     * Maps a domain {@link Amount} to its embeddable form. Kept for parity with
     * {@link PayrollPeriodEarnerRankingPersistenceMapper}; not used by the percentile
     * entity itself (which stores flat columns), but harmless and lets future
     * embeddable-based additions reuse it.
     *
     * @param amount source amount
     * @return mapped embeddable
     */
    AmountEmbeddable toEmbeddable(Amount amount);

    /**
     * Maps an {@link AmountEmbeddable} back to the domain {@link Amount}.
     *
     * @param embeddable source embeddable
     * @return mapped amount
     */
    Amount toAmount(AmountEmbeddable embeddable);
}