package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.CountryRepository;
import com.salary.management.domain.repository.EmployeePeriodicAssignmentRepository;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.PayrollSummaryRepository;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.embeddable.AmountsEmbeddable;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence adapter implementing {@link PayrollSummaryRepository}. */
@Component
@RequiredArgsConstructor
public class PayrollSummaryRepositoryAdapter implements PayrollSummaryRepository {

    private final PayrollSummaryJpaRepository jpaRepository;
    private final EmployeeJpaRepository employeeJpaRepository;
    private final EmployeePeriodicAssignmentJpaRepository assignmentJpaRepository;
    private final PayrollSummaryPersistenceMapper mapper;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final CountryRepository countryRepository;

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
    public DataPage<PayrollSummary> pageNonExistent(PayrollSummaryCriteria criteria) {
        if (!Boolean.TRUE.equals(criteria.nonExistent())) {
            throw new IllegalArgumentException(
                    "pageNonExistent requires criteria.nonExistent = true");
        }
        if (criteria.payrollPeriodId() == null && (criteria.rangeStart() == null || criteria.rangeEnd() == null)) {
            throw new IllegalArgumentException(
                    "pageNonExistent requires criteria.payrollPeriodId or criteria.rangeStart and criteria.rangeEnd");
        }
        LocalDate criteriaStartDate = criteria.rangeStart();
        LocalDate criteriaEndDate = criteria.rangeEnd();
        if(criteria.payrollPeriodId() != null) {// The period has precedence on the range date
            PayrollPeriod period = payrollPeriodRepository.findById(criteria.payrollPeriodId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Payroll period not found: " + criteria.payrollPeriodId()));
            criteriaStartDate = period.getStartDate();
            criteriaEndDate = period.getEndDate();
        }

        Pageable pageable = PageRequest.of(
                criteria.page(),
                criteria.size(),
                Sort.by(Sort.Direction.ASC, "startDate")
                        .and(Sort.by(Sort.Direction.ASC, "id")));

        Page<PayrollSummaryEntity> page =
                assignmentJpaRepository.findMissingSummaryPage(
                        criteriaStartDate,
                        criteriaEndDate,
                        criteria.payrollFrequency(),
                        criteria.employeeId(),
                        nullIfEmpty(criteria.anyCountryIds()),
                        nullIfEmpty(criteria.anyDepartmentIds()),
                        nullIfEmpty(criteria.anyRoleIds()),
                        nullIfEmpty(criteria.anyEmploymentTypes()),
                        pageable);

        List<PayrollSummary> content = page.getContent().stream().map(this::toSyntheticPayrollSummary).toList();

        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public LocalDate latestLinkedPeriodStart(UUID assignmentId) {
        return jpaRepository.latestLinkedPeriodStart(assignmentId);
    }

    @Override
    public boolean hasAnySummaryForAssignment(UUID assignmentId) {
        return jpaRepository.hasAnySummaryForAssignment(assignmentId);
    }

    @Override
    public int updateStatus(UUID id, PayrollSummaryStatus expectedStatus,
                            PayrollSummaryStatus newStatus) {
        return jpaRepository.updateStatus(id, expectedStatus, newStatus);
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

    private PayrollSummary toSyntheticPayrollSummary(PayrollSummaryEntity payrollSummaryEntity) {
        Country country = countryRepository.findById(payrollSummaryEntity.getAssignmentCountryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Country not found: " + payrollSummaryEntity.getAssignmentCountryId()));
        String currency = country.getCurrencyCode();
        if (currency == null || currency.isBlank()) {
            throw new IllegalStateException(
                    "Country " + country.getId() + " has no currency code; cannot build synthetic summary");
        }
        AmountsEmbeddable amountWithCurrency = payrollSummaryEntity.getAmounts();
        amountWithCurrency.setCurrency(currency);
        payrollSummaryEntity.setAmounts(amountWithCurrency);
        return mapper.toDomain(payrollSummaryEntity);
    }

    private static <T> Collection<T> nullIfEmpty(Set<T> set) {
        return set == null || set.isEmpty() ? null : set;
    }
}