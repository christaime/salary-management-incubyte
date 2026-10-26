package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollPeriodEarnerPercentile;
import com.salary.management.domain.model.PayrollPeriodEarnerRanking;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.values.Amount;
import com.salary.management.domain.model.values.RankedSummary;
import com.salary.management.domain.model.values.RankingAndPercentileSnapshot;
import com.salary.management.domain.repository.PayrollPeriodEarnerRankingRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodEarnerRankingCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.*;
import com.salary.management.infrastructure.persistence.mapper.PayrollPeriodEarnerPercentilePersistenceMapper;
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
    private final PayrollPeriodEarnerPercentilePersistenceMapper periodEarnerPercentilePersistenceMapper;
    private final PayrollPeriodEarnerPercentileJpaRepository earnerPercentileJpaRepository;

    @Override
    public void saveAll(List<PayrollPeriodEarnerRanking> rankings) {
        List<PayrollPeriodEarnerRankingEntity> entities = rankings.stream()
                .map(this::toValidatedEntity)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    public void saveAll(List<PayrollPeriodEarnerRanking> rankings, List<PayrollPeriodEarnerPercentile> percentiles) {
        List<PayrollPeriodEarnerRankingEntity> entities = rankings.stream()
                .map(this::toValidatedEntity)
                .toList();
        jpaRepository.saveAll(entities);
        List<PayrollPeriodEarnerPercentileEntity> percentileEntities = percentiles.stream()
                .map(this::toValidatedPercentileEntity)
                .toList();
        earnerPercentileJpaRepository.saveAll(percentileEntities);
    }

    @Override
    public List<PayrollPeriodEarnerRanking> findByPayrollPeriodId(UUID payrollPeriodId) {
        return jpaRepository.findByPayrollPeriodId(payrollPeriodId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PayrollPeriodEarnerPercentile> findPercentileByPayrollPeriodId(UUID payrollPeriodId) {
        return earnerPercentileJpaRepository.findByPayrollPeriodId(payrollPeriodId).stream()
                .map(this::toPercentileDomain)
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
    public void deleteDraftsPercentileByPayrollPeriodId(UUID payrollPeriodId) {
        earnerPercentileJpaRepository.deleteDraftsByPayrollPeriodId(payrollPeriodId);
    }

    @Override
    public RankingAndPercentileSnapshot rankAndPercentile(
            UUID payrollPeriodId,
            String referenceCurrencyCode,
            GroupKey key,
            int limit){
        PayrollPeriodEntity period = payrollPeriodJpaRepository.findById(payrollPeriodId).orElseThrow();
        DynamicRankingBuilder.RankingAndPercentileResult result = dynamicRankingBuilder.rankAndComputePercentilesForPeriod(period, referenceCurrencyCode, key, limit);
        return toSnapshot(result);
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
        UUID countryId = ranking.getCountry() != null ? ranking.getCountry().getId() : null;
        if (countryId != null && !countryJpaRepository.existsById(countryId)) {
            throw new EntityNotFoundException("Country not found: " + countryId);
        }
        UUID departmentId = ranking.getDepartment() != null ? ranking.getDepartment().getId() : null;
        if (departmentId != null && !departmentJpaRepository.existsById(departmentId)) {
            throw new EntityNotFoundException("Department not found: " + departmentId);
        }
        UUID roleId = ranking.getRole() != null ? ranking.getRole().getId() : null;
        if (roleId != null && !roleJpaRepository.existsById(roleId)) {
            throw new EntityNotFoundException("Role not found: " + roleId);
        }
        UUID employeeId = ranking.getEmployee().getId();
        if (!employeeJpaRepository.existsById(employeeId)) {
            throw new EntityNotFoundException("Employee not found: " + employeeId);
        }
        return mapper.toEntity(ranking);
    }

    private PayrollPeriodEarnerPercentileEntity toValidatedPercentileEntity(
            PayrollPeriodEarnerPercentile percentile) {
        UUID payrollPeriodId = percentile.getPayrollPeriod().getId();
        if (!payrollPeriodJpaRepository.existsById(payrollPeriodId)) {
            throw new EntityNotFoundException(
                    "PayrollPeriod not found: " + payrollPeriodId);
        }
        UUID countryId = percentile.getCountry() != null ? percentile.getCountry().getId() : null;
        if (countryId != null && !countryJpaRepository.existsById(countryId)) {
            throw new EntityNotFoundException("Country not found: " + countryId);
        }
        UUID departmentId = percentile.getDepartment() != null ? percentile.getDepartment().getId() : null;
        if (departmentId != null && !departmentJpaRepository.existsById(departmentId)) {
            throw new EntityNotFoundException("Department not found: " + departmentId);
        }
        UUID roleId = percentile.getRole() != null ? percentile.getRole().getId() : null;
        if (roleId != null && !roleJpaRepository.existsById(roleId)) {
            throw new EntityNotFoundException("Role not found: " + roleId);
        }
        return periodEarnerPercentilePersistenceMapper.toEntity(percentile);
    }

    private PayrollPeriodEarnerRanking toDomain(PayrollPeriodEarnerRankingEntity entity) {
        PayrollPeriodEntity payrollPeriod = payrollPeriodJpaRepository
                .findById(entity.getPayrollPeriodId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "PayrollPeriod not found: " + entity.getPayrollPeriodId()));
        CountryEntity country = null;
        if(entity.getCountryId() != null) {
            country = countryJpaRepository
                    .findById(entity.getCountryId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Country not found: " + entity.getCountryId()));
        }
        DepartmentEntity department = null;
        if(entity.getDepartmentId() != null) {
            department = departmentJpaRepository
                    .findById(entity.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Department not found: " + entity.getDepartmentId()));
        }
        RoleEntity role = null;
        if(entity.getRoleId() != null) {
            role = roleJpaRepository
                    .findById(entity.getRoleId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Role not found: " + entity.getRoleId()));
        }
        EmployeeEntity employee = employeeJpaRepository
                .findById(entity.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Employee not found: " + entity.getEmployeeId()));
        return mapper.toDomain(entity, payrollPeriod, country, department, role, employee);
    }

    private PayrollPeriodEarnerPercentile toPercentileDomain(PayrollPeriodEarnerPercentileEntity entity) {
        PayrollPeriodEntity payrollPeriod = payrollPeriodJpaRepository
                .findById(entity.getPayrollPeriodId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "PayrollPeriod not found: " + entity.getPayrollPeriodId()));
        CountryEntity country = null;
        if(entity.getCountryId() != null) {
            country = countryJpaRepository
                    .findById(entity.getCountryId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Country not found: " + entity.getCountryId()));
        }
        DepartmentEntity department = null;
        if(entity.getDepartmentId() != null) {
            department = departmentJpaRepository
                    .findById(entity.getDepartmentId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Department not found: " + entity.getDepartmentId()));
        }
        RoleEntity role = null;
        if(entity.getRoleId() != null) {
            role = roleJpaRepository
                    .findById(entity.getRoleId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Role not found: " + entity.getRoleId()));
        }
        return periodEarnerPercentilePersistenceMapper.toDomain(entity, payrollPeriod, country, department, role);
    }

    private RankingAndPercentileSnapshot toSnapshot(DynamicRankingBuilder.RankingAndPercentileResult result){
        if(result == null){
            return null;
        }
        return new RankingAndPercentileSnapshot(result.rankings().stream().map(this::toDomain).toList(),result.percentiles().stream().map(this::toPercentileDomain).toList());
    }
}