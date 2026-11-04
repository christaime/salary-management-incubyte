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
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Persistence adapter implementing {@link PayrollPeriodAggregateRepository}. */
@Slf4j
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
        if (aggregates == null || aggregates.isEmpty()) {
            log.debug("saveAll(aggregates) called with no entries; skipping");
            return;
        }
        log.debug("Saving {} payroll period aggregate(s)", aggregates.size());
        List<PayrollPeriodAggregateEntity> entities = aggregates.stream()
                .map(this::toValidatedEntity)
                .toList();
        jpaRepository.saveAll(entities);
        log.debug("Saved {} payroll period aggregate(s)", entities.size());
    }

    @Override
    public List<PayrollPeriodAggregate> findByPayrollPeriodId(UUID payrollPeriodId) {
        List<PayrollPeriodAggregate> result = jpaRepository
                .findByPayrollPeriodId(payrollPeriodId).stream()
                .map(this::toDomain)
                .toList();
        log.debug("findByPayrollPeriodId({}) returned {} aggregate(s)",
                payrollPeriodId, result.size());
        return result;
    }

    @Override
    public List<PayrollPeriodAggregate> list(PayrollPeriodAggregateCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<PayrollPeriodAggregateEntity> entities = jpaRepository.findAll(
                PayrollPeriodAggregateSpecification.build(criteria), sort);
        log.debug("list(aggregateCriteria) returned {} aggregate(s)", entities.size());
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public int deleteDraftsByPayrollPeriodId(UUID payrollPeriodId) {
        log.debug("Deleting DRAFT payroll period aggregates for period {}", payrollPeriodId);
        return jpaRepository.deleteDraftsByPayrollPeriodId(payrollPeriodId);
    }

    @Override
    public int validateDraftsByPeriodId(UUID periodId) {
        int updated = jpaRepository.validateDraftsByPayrollPeriodId(periodId);
        log.debug("Period {}: {} aggregate(s) promoted to VALIDATED", periodId, updated);
        return updated;
    }

    @Override
    public List<GroupedSummary> aggregateByGroup(UUID payrollPeriodId,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {
        log.debug("aggregateByGroup period={} range=[{}, {}]",
                payrollPeriodId, startDate, endDate);

        List<GroupedSummary> result = jpaRepository.aggregateByGroup(startDate, endDate)
                .stream()
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

        log.debug("aggregateByGroup period={} returned {} group(s)",
                payrollPeriodId, result.size());
        return result;
    }

    @Override
    public BigDecimal sumReferenceTotalByPeriodId(UUID payrollPeriodId) {
        BigDecimal total = jpaRepository.sumReferenceTotalByPeriodId(payrollPeriodId);
        log.trace("sumReferenceTotalByPeriodId({}) = {}", payrollPeriodId, total);
        return total;
    }

    @Override
    public BigDecimal sumReferenceTotalByPeriodAndDimension(
            UUID payrollPeriodId, UUID countryId, UUID departmentId,
            UUID roleId, EmploymentType employmentType) {
        BigDecimal total = jpaRepository.sumReferenceTotalByPeriodAndDimension(
                payrollPeriodId, countryId, departmentId, roleId, employmentType);
        log.trace("sumReferenceTotalByPeriodAndDimension(period={}, country={}, "
                        + "department={}, role={}, employmentType={}) = {}",
                payrollPeriodId, countryId, departmentId, roleId, employmentType, total);
        return total;
    }

    /**
     * Verifies that the aggregate's foreign references exist before mapping. Throws
     * {@link EntityNotFoundException} on the first missing reference.
     */
    private PayrollPeriodAggregateEntity toValidatedEntity(PayrollPeriodAggregate aggregate) {
        UUID payrollPeriodId = aggregate.getPayrollPeriod().getId();
        if (!payrollPeriodJpaRepository.existsById(payrollPeriodId)) {
            log.warn("Aggregate rejected: payroll period {} not found", payrollPeriodId);
            throw new EntityNotFoundException(
                    "PayrollPeriod not found: " + payrollPeriodId);
        }
        UUID countryId = aggregate.getCountry().getId();
        if (!countryJpaRepository.existsById(countryId)) {
            log.warn("Aggregate rejected: country {} not found", countryId);
            throw new EntityNotFoundException("Country not found: " + countryId);
        }
        UUID departmentId = aggregate.getDepartment().getId();
        if (!departmentJpaRepository.existsById(departmentId)) {
            log.warn("Aggregate rejected: department {} not found", departmentId);
            throw new EntityNotFoundException("Department not found: " + departmentId);
        }
        UUID roleId = aggregate.getRole().getId();
        if (!roleJpaRepository.existsById(roleId)) {
            log.warn("Aggregate rejected: role {} not found", roleId);
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