package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
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
import com.salary.management.infrastructure.persistence.projection.SummaryValidationRow;
import com.salary.management.infrastructure.persistence.repository.EmployeeJpaRepository;
import com.salary.management.infrastructure.persistence.repository.EmployeePeriodicAssignmentJpaRepository;
import com.salary.management.infrastructure.persistence.repository.PayrollSummaryJpaRepository;
import com.salary.management.infrastructure.persistence.specification.PayrollSummarySpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Persistence adapter implementing {@link PayrollSummaryRepository}. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayrollSummaryRepositoryAdapter implements PayrollSummaryRepository {

    private final PayrollSummaryJpaRepository jpaRepository;
    private final EmployeeJpaRepository employeeJpaRepository;
    private final EmployeePeriodicAssignmentJpaRepository assignmentJpaRepository;
    private final PayrollSummaryPersistenceMapper mapper;
    private final PayrollPeriodRepository payrollPeriodRepository;
    private final CountryRepository countryRepository;

    // ------------------------------------------------------------------
    // Standard CRUD
    // ------------------------------------------------------------------

    @Override
    public PayrollSummary save(PayrollSummary summary) {
        log.debug("Saving payroll summary id={} employeeId={} periodId={}",
                summary.getId(), summary.getEmployeeId(),
                summary.getPayrollPeriod() != null
                        ? summary.getPayrollPeriod().getId() : null);

        EmployeeEntity employee = employeeJpaRepository
                .findById(summary.getEmployeeId())
                .orElseThrow(() -> {
                    log.warn("Save rejected: employee {} not found for summary {}",
                            summary.getEmployeeId(), summary.getId());
                    return new EntityNotFoundException(
                            "Employee not found: " + summary.getEmployeeId());
                });
        EmployeePeriodicAssignmentEntity assignment = assignmentJpaRepository
                .findById(summary.getEmployeePeriodicAssignmentId())
                .orElseThrow(() -> {
                    log.warn("Save rejected: assignment {} not found for summary {}",
                            summary.getEmployeePeriodicAssignmentId(), summary.getId());
                    return new EntityNotFoundException(
                            "EmployeePeriodicAssignment not found: "
                                    + summary.getEmployeePeriodicAssignmentId());
                });

        PayrollSummaryEntity saved =
                jpaRepository.save(mapper.toEntity(summary, employee, assignment));
        log.debug("Saved payroll summary id={} status={}",
                saved.getId(), saved.getStatus());
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PayrollSummary> findById(UUID id) {
        Optional<PayrollSummary> result = jpaRepository.findById(id).map(mapper::toDomain);
        if (result.isEmpty()) {
            log.debug("Payroll summary {} not found", id);
        }
        return result;
    }

    @Override
    public Optional<PayrollSummary> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId, UUID payrollPeriodId) {
        return jpaRepository
                .findByEmployeeIdAndPayrollPeriodId(employeeId, payrollPeriodId)
                .map(mapper::toDomain);
    }

    @Override
    public void deleteById(UUID id) {
        log.debug("Deleting payroll summary id={}", id);
        jpaRepository.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Criteria-based reads
    // ------------------------------------------------------------------

    @Override
    public List<PayrollSummary> list(PayrollSummaryCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<PayrollSummaryEntity> entities = jpaRepository.findAll(
                PayrollSummarySpecification.build(criteria), sort);
        log.debug("list(criteria) returned {} summary/ies", entities.size());
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public DataPage<PayrollSummary> page(PayrollSummaryCriteria criteria) {
        Page<PayrollSummaryEntity> page = jpaRepository.findAll(
                PayrollSummarySpecification.build(criteria),
                CriteriaUtils.toPageable(criteria));
        log.debug("page(criteria) page={} size={} returned {} of {}",
                page.getNumber(), page.getSize(),
                page.getNumberOfElements(), page.getTotalElements());
        return toDataPage(page);
    }

    @Override
    public DataPage<PayrollSummary> pageNonExistent(PayrollSummaryCriteria criteria) {
        if (!Boolean.TRUE.equals(criteria.nonExistent())) {
            throw new IllegalArgumentException(
                    "pageNonExistent requires criteria.nonExistent = true");
        }
        if (criteria.payrollPeriodId() == null
                && (criteria.rangeStart() == null || criteria.rangeEnd() == null)) {
            throw new IllegalArgumentException(
                    "pageNonExistent requires criteria.payrollPeriodId "
                            + "or criteria.rangeStart and criteria.rangeEnd");
        }

        LocalDate criteriaStartDate = criteria.rangeStart();
        LocalDate criteriaEndDate = criteria.rangeEnd();
        if (criteria.payrollPeriodId() != null) {
            // The period has precedence over the range dates.
            PayrollPeriod period = payrollPeriodRepository
                    .findById(criteria.payrollPeriodId())
                    .orElseThrow(() -> {
                        log.warn("pageNonExistent: payroll period {} not found",
                                criteria.payrollPeriodId());
                        return new IllegalArgumentException(
                                "Payroll period not found: " + criteria.payrollPeriodId());
                    });
            criteriaStartDate = period.getStartDate();
            criteriaEndDate = period.getEndDate();
        }

        log.debug("pageNonExistent range=[{}, {}] frequency={} employeeId={}",
                criteriaStartDate, criteriaEndDate,
                criteria.payrollFrequency(), criteria.employeeId());

        Pageable pageable = PageRequest.of(
                criteria.page(),
                criteria.size(),
                Sort.by(Sort.Direction.ASC, "startDate")
                        .and(Sort.by(Sort.Direction.ASC, "id")));

        Page<PayrollSummaryEntity> page = assignmentJpaRepository.findMissingSummaryPage(
                criteriaStartDate,
                criteriaEndDate,
                criteria.payrollFrequency(),
                criteria.employeeId(),
                nullIfEmpty(criteria.anyCountryIds()),
                nullIfEmpty(criteria.anyDepartmentIds()),
                nullIfEmpty(criteria.anyRoleIds()),
                nullIfEmpty(criteria.anyEmploymentTypes()),
                pageable);

        log.debug("pageNonExistent found {} missing summar(ies) across {} total",
                page.getNumberOfElements(), page.getTotalElements());

        List<PayrollSummary> content = page.getContent().stream()
                .map(this::toSyntheticPayrollSummary)
                .toList();

        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

    // ------------------------------------------------------------------
    // Assignment-linked reads
    // ------------------------------------------------------------------

    @Override
    public LocalDate latestLinkedPeriodStart(UUID assignmentId) {
        return jpaRepository.latestLinkedPeriodStart(assignmentId);
    }

    @Override
    public boolean hasAnySummaryForAssignment(UUID assignmentId) {
        return jpaRepository.hasAnySummaryForAssignment(assignmentId);
    }

    // ------------------------------------------------------------------
    // Bulk validation read
    // ------------------------------------------------------------------

    @Override
    public DataPage<SummaryValidationRow> findValidationRows(
            LocalDate rangeStart,
            LocalDate rangeEnd,
            UUID employeeId,
            Set<UUID> anyCountryIds,
            Set<UUID> anyDepartmentIds,
            Set<UUID> anyRoleIds,
            Set<EmploymentType> anyEmploymentTypes,
            PayrollPeriodFrequency payrollFrequency,
            UUID afterId,
            int page,
            int size) {

        // No Sort here: the JPQL query carries ORDER BY s.id, and a Sort on a
        // SELECT NEW query is unreliable (Spring Data resolves it against the
        // projection type, not the FROM root). Keyset paging uses afterId,
        // never the offset, so `page` is always 0.
        Pageable pageable = PageRequest.of(page, size);

        log.trace("findValidationRows range=[{}, {}] afterId={} page={} size={}",
                rangeStart, rangeEnd, afterId, page, size);

        Page<SummaryValidationRow> rows = jpaRepository.findValidationRows(
                rangeStart,
                rangeEnd,
                employeeId,
                nullIfEmpty(anyCountryIds),
                nullIfEmpty(anyDepartmentIds),
                nullIfEmpty(anyRoleIds),
                nullIfEmpty(anyEmploymentTypes),
                payrollFrequency,
                afterId,
                pageable);

        log.debug("findValidationRows returned {} row(s) (totalElements={})",
                rows.getNumberOfElements(), rows.getTotalElements());

        return new DataPage<>(
                rows.getContent(),
                rows.getNumber(),
                rows.getSize(),
                rows.getTotalElements());
    }

    // ------------------------------------------------------------------
    // Status transitions
    // ------------------------------------------------------------------

    @Override
    public int updateStatus(UUID id, PayrollSummaryStatus expectedStatus,
                            PayrollSummaryStatus newStatus) {
        int updated = jpaRepository.updateStatus(id, expectedStatus, newStatus);
        if (updated == 0) {
            log.warn("updateStatus affected 0 rows: id={} expected={} (missing or status changed)",
                    id, expectedStatus);
        } else {
            log.debug("updateStatus id={} {} -> {}", id, expectedStatus, newStatus);
        }
        return updated;
    }

    @Override
    public int bulkUpdateStatus(Collection<UUID> ids,
                                PayrollSummaryStatus expected,
                                PayrollSummaryStatus target) {
        if (ids == null || ids.isEmpty()) {
            log.debug("bulkUpdateStatus called with no ids; skipping");
            return 0;
        }
        int updated = jpaRepository.bulkUpdateStatus(ids, expected, target);
        if (updated != ids.size()) {
            log.warn("bulkUpdateStatus affected {} of {} rows ({} -> {}); "
                            + "some rows were concurrently changed or deleted",
                    updated, ids.size(), expected, target);
        } else {
            log.debug("bulkUpdateStatus updated {} row(s) ({} -> {})",
                    updated, expected, target);
        }
        return updated;
    }

    @Override
    public Set<UUID> findIdsWithStatus(Collection<UUID> ids, PayrollSummaryStatus status) {
        Set<UUID> result = jpaRepository.findIdsWithStatus(ids, status);
        log.trace("findIdsWithStatus({} ids, status={}) returned {}",
                ids == null ? 0 : ids.size(), status, result.size());
        return result;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

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
        Country country = countryRepository
                .findById(payrollSummaryEntity.getAssignmentCountryId())
                .orElseThrow(() -> {
                    log.warn("Synthetic summary: country {} not found for assignment {}",
                            payrollSummaryEntity.getAssignmentCountryId(),
                            payrollSummaryEntity.getEmployeePeriodicAssignmentId());
                    return new IllegalArgumentException(
                            "Country not found: "
                                    + payrollSummaryEntity.getAssignmentCountryId());
                });
        String currency = country.getCurrencyCode();
        if (currency == null || currency.isBlank()) {
            log.error("Synthetic summary: country {} has no currency code",
                    country.getId());
            throw new IllegalStateException(
                    "Country " + country.getId()
                            + " has no currency code; cannot build synthetic summary");
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