package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.repository.PayrollSummaryRepository;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity;
import com.salary.management.infrastructure.persistence.mapper.PayrollSummaryPersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.EmployeeJpaRepository;
import com.salary.management.infrastructure.persistence.repository.EmployeePeriodicAssignmentJpaRepository;
import com.salary.management.infrastructure.persistence.repository.PayrollSummaryJpaRepository;
import com.salary.management.infrastructure.persistence.specification.PayrollSummarySpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link PayrollSummaryRepository}. */
@Component
@RequiredArgsConstructor
public class PayrollSummaryRepositoryAdapter implements PayrollSummaryRepository {

    private final PayrollSummaryJpaRepository jpaRepository;
    private final EmployeeJpaRepository employeeJpaRepository;
    private final EmployeePeriodicAssignmentJpaRepository assignmentJpaRepository;
    private final PayrollSummaryPersistenceMapper mapper;

    @Override
    public PayrollSummary save(PayrollSummary summary) {
        EmployeeEntity employee = employeeJpaRepository
                .findById(summary.getEmployeeId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Employee not found: " + summary.getEmployeeId()));
        EmployeePeriodicAssignmentEntity assignment = assignmentJpaRepository
                .findById(summary.getEmployeePeriodicAssignmentId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "EmployeePeriodicAssignment not found: "
                                + summary.getEmployeePeriodicAssignmentId()));

        PayrollSummaryEntity saved =
                jpaRepository.save(mapper.toEntity(summary, employee, assignment));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PayrollSummary> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PayrollSummary> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId, UUID payrollPeriodId) {
        return jpaRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                .map(mapper::toDomain);
    }

    @Override
    public List<PayrollSummary> list(PayrollSummaryCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<PayrollSummaryEntity> entities = jpaRepository.findAll(
                PayrollSummarySpecification.build(criteria), sort);
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public DataPage<PayrollSummary> page(PayrollSummaryCriteria criteria) {
        Page<PayrollSummaryEntity> page = jpaRepository.findAll(
                PayrollSummarySpecification.build(criteria),
                CriteriaUtils.toPageable(criteria));
        return toDataPage(page);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private DataPage<PayrollSummary> toDataPage(Page<PayrollSummaryEntity> page) {
        List<PayrollSummary> content =
                page.getContent().stream().map(mapper::toDomain).toList();
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}