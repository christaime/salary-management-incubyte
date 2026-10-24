package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.values.Amount;
import com.salary.management.domain.model.values.RankedSummary;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodEarnerRankingCriteria;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import com.salary.management.infrastructure.persistence.mapper.PayrollPeriodEarnerRankingPersistenceMapper;
import com.salary.management.infrastructure.persistence.projection.RankedRow;
import com.salary.management.infrastructure.persistence.repository.*;
import com.salary.management.infrastructure.persistence.specification.PayrollPeriodEarnerRankingSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Persistence adapter implementing {@link PayrollPeriodEarnerRankingRepository}. */
@Component
@RequiredArgsConstructor
public class PayrollPeriodEarnerRankingRepositoryAdapter
        implements PayrollPeriodEarnerRankingRepository {

    private final PayrollPeriodEarnerRankingJpaRepository jpaRepository;
    private final PayrollPeriodJpaRepository payrollPeriodJpaRepository;
    private final CountryJpaRepository countryJpaRepository;
    private final DepartmentJpaRepository departmentJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final EmployeeJpaRepository employeeJpaRepository;
    private final PayrollPeriodEarnerRankingPersistenceMapper mapper;
    private final DynamicRankingBuilder dynamicRankingBuilder;

    @Override
    public void saveAll(List<PayrollPeriodEarnerRanking> rankings) {
        List<PayrollPeriodEarnerRankingEntity> entities = rankings.stream()
                .map(this::toValidatedEntity)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public List<PayrollPeriodEarnerRanking> findByPayrollPeriodId(UUID payrollPeriodId) {
        return jpaRepository.findByPayrollPeriodId(payrollPeriodId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PayrollPeriodEarnerRanking> list(PayrollPeriodEarnerRankingCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<PayrollPeriodEarnerRankingEntity> entities = jpaRepository.findAll(
                PayrollPeriodEarnerRankingSpecification.build(criteria), sort);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteDraftsByPayrollPeriodId(UUID payrollPeriodId) {
        jpaRepository.deleteDraftsByPayrollPeriodId(payrollPeriodId);
    }

    @Override
    public List<PayrollPeriodEarnerRankingEntity> rank(
            UUID payrollPeriodId,
            String referenceCurrencyCode,
            EarnerRankingDirection direction,
            GroupKey key,
            int limit){
        return dynamicRankingBuilder.rank(payrollPeriodId, referenceCurrencyCode, direction, key, limit);
    }


    private RankedSummary toRankedSummary(RankedRow row, EarnerRankingDirection direction) {
        return new RankedSummary(
                row.getPayrollPeriodId(),
                row.getCountryId(),
                row.getDepartmentId(),
                row.getRoleId(),
                EmploymentType.valueOf(row.getEmploymentType()),
                direction,
                row.getRank(),
                row.getEmployeeId(),
                amount(row.getOriginalTotal(), row.getOriginalCurrency()),
                amount(row.getReferenceTotal(), row.getReferenceCurrency()));
    }

    private Amount amount(java.math.BigDecimal value, String currency) {
        return Amount.builder().amount(value).currency(currency).build();
    }
    /**
     * Verifies that the ranking's foreign references exist before mapping. Throws
     * {@link EntityNotFoundException} on the first missing reference.
     */
    private PayrollPeriodEarnerRankingEntity toValidatedEntity(
            PayrollPeriodEarnerRanking ranking) {
        UUID payrollPeriodId = ranking.getPayrollPeriod().getId();
        if (!payrollPeriodJpaRepository.existsById(payrollPeriodId)) {
            throw new EntityNotFoundException(
                    "PayrollPeriod not found: " + payrollPeriodId);
        }
        UUID countryId = ranking.getCountry().getId();
        if (!countryJpaRepository.existsById(countryId)) {
            throw new EntityNotFoundException("Country not found: " + countryId);
        }
        UUID departmentId = ranking.getDepartment().getId();
        if (!departmentJpaRepository.existsById(departmentId)) {
            throw new EntityNotFoundException("Department not found: " + departmentId);
        }
        UUID roleId = ranking.getRole().getId();
        if (!roleJpaRepository.existsById(roleId)) {
            throw new EntityNotFoundException("Role not found: " + roleId);
        }
        UUID employeeId = ranking.getEmployee().getId();
        if (!employeeJpaRepository.existsById(employeeId)) {
            throw new EntityNotFoundException("Employee not found: " + employeeId);
        }
        return mapper.toEntity(ranking);
    }

    private PayrollPeriodEarnerRanking toDomain(PayrollPeriodEarnerRankingEntity entity) {
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
        EmployeeEntity employee = employeeJpaRepository
                .findById(entity.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Employee not found: " + entity.getEmployeeId()));
        return mapper.toDomain(entity, payrollPeriod, country, department, role, employee);
    }
}