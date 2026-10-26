package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.values.GroupedSummary;
import com.salary.management.domain.repository.PayrollPeriodAggregateRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodAggregateCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodAggregateEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import com.salary.management.infrastructure.persistence.mapper.PayrollPeriodAggregatePersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.*;
import com.salary.management.infrastructure.persistence.specification.PayrollPeriodAggregateSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Persistence adapter implementing {@link PayrollPeriodAggregateRepository}. */
@Component
@RequiredArgsConstructor
public class PayrollPeriodAggregateRepositoryAdapter
        implements PayrollPeriodAggregateRepository {

    private final PayrollPeriodAggregateJpaRepository jpaRepository;
    private final PayrollPeriodJpaRepository payrollPeriodJpaRepository;
    private final CountryJpaRepository countryJpaRepository;
    private final DepartmentJpaRepository departmentJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final PayrollPeriodAggregatePersistenceMapper mapper;

    @Override
    public void saveAll(List<PayrollPeriodAggregate> aggregates) {
        List<PayrollPeriodAggregateEntity> entities = aggregates.stream()
                .map(this::toValidatedEntity)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<PayrollPeriodAggregate> findByPayrollPeriodId(UUID payrollPeriodId) {
        return jpaRepository.findByPayrollPeriodId(payrollPeriodId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PayrollPeriodAggregate> list(PayrollPeriodAggregateCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<PayrollPeriodAggregateEntity> entities = jpaRepository.findAll(
                PayrollPeriodAggregateSpecification.build(criteria), sort);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteDraftsByPayrollPeriodId(UUID payrollPeriodId) {
        jpaRepository.deleteDraftsByPayrollPeriodId(payrollPeriodId);
    }

    @Override
    public List<GroupedSummary> aggregateByGroup(UUID payrollPeriodId, LocalDate startDate, LocalDate endDate) {
        return jpaRepository.aggregateByGroup(startDate, endDate).stream()
                .map(row -> new GroupedSummary(
                        payrollPeriodId,
                        row.getCountryId(),
                        row.getDepartmentId(),
                        row.getRoleId(),
                        EmploymentType.valueOf(row.getEmploymentType()),
                        row.getCurrency(),
                        row.getEmployeeCount().intValue(),
                        row.getBase(),
                        row.getVariable(),
                        row.getAllowances(),
                        row.getEmployerTaxes(),
                        row.getTotal()))
                .toList();
    }

    /**
     * Verifies that the aggregate's foreign references exist before mapping. Throws
     * {@link EntityNotFoundException} on the first missing reference.
     */
    private PayrollPeriodAggregateEntity toValidatedEntity(PayrollPeriodAggregate aggregate) {
        UUID payrollPeriodId = aggregate.getPayrollPeriod().getId();
        if (!payrollPeriodJpaRepository.existsById(payrollPeriodId)) {
            throw new EntityNotFoundException(
                    "PayrollPeriod not found: " + payrollPeriodId);
        }
        UUID countryId = aggregate.getCountry().getId();
        if (!countryJpaRepository.existsById(countryId)) {
            throw new EntityNotFoundException("Country not found: " + countryId);
        }
        UUID departmentId = aggregate.getDepartment().getId();
        if (!departmentJpaRepository.existsById(departmentId)) {
            throw new EntityNotFoundException("Department not found: " + departmentId);
        }
        UUID roleId = aggregate.getRole().getId();
        if (!roleJpaRepository.existsById(roleId)) {
            throw new EntityNotFoundException("Role not found: " + roleId);
        }
        return mapper.toEntity(aggregate);
    }

    private PayrollPeriodAggregate toDomain(PayrollPeriodAggregateEntity entity) {
        PayrollPeriodEntity payrollPeriod = payrollPeriodJpaRepository
                .findById(entity.getPayrollPeriodId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "PayrollPeriod not found: " + entity.getPayrollPeriodId()));
        CountryEntity country = countryJpaRepository
                .findById(entity.getCountryId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Country not found: " + entity.getCountryId()));
        DepartmentEntity department = departmentJpaRepository
                .findById(entity.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Department not found: " + entity.getDepartmentId()));
        RoleEntity role = roleJpaRepository
                .findById(entity.getRoleId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Role not found: " + entity.getRoleId()));
        return mapper.toDomain(entity, payrollPeriod, country, department, role);
    }
}