package com.salary.management.application.service;

import com.salary.management.application.dto.payroll.*;
import com.salary.management.application.mapper.PayrollSummaryApiMapper;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.*;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.*;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import com.salary.management.infrastructure.persistence.projection.SummaryValidationRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/** Reads, creates, edits, deletes and validates payroll summaries. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayrollSummaryService {

    private static final int BULK_PAGE_SIZE = 100;

    private final PayrollSummaryRepository summaryRepository;
    private final PayrollPeriodRepository periodRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeePeriodicAssignmentRepository assignmentRepository;
    private final CountryRepository countryRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PayrollSummaryApiMapper mapper;
    private final PayrollSummaryValidator validator;

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DataPage<PayrollSummaryDto> page(PayrollSummarySearchRequest request) {
        PayrollSummaryCriteria criteria = toCriteria(request);
        DataPage<PayrollSummary> source = Boolean.TRUE.equals(criteria.nonExistent()) ?
                summaryRepository.pageNonExistent(criteria) : summaryRepository.page(criteria);
        List<PayrollSummaryDto> content = source.content().stream()
                .map(this::toDto)
                .toList();
        return new DataPage<>(content, source.page(), source.size(), source.totalElements());
    }

    @Transactional(readOnly = true)
    public PayrollSummaryDetailDto findById(UUID id) {
        PayrollSummary summary = summaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollSummary not found: " + id));
        return toDetail(summary);
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Transactional
    public PayrollSummaryDetailDto create(PayrollSummaryCreateRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + request.employeeId()));
        PayrollPeriod period = periodRepository.findById(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollPeriod not found: " + request.periodId()));
        if (Boolean.TRUE.equals(period.getClosed())) {
            throw new ConflictException(
                    "PERIOD_CLOSED",
                    "Payroll period " + period.getName() + " is closed",
                    Map.of("periodId", period.getId(), "periodName", period.getName()));
        }
        EmployeePeriodicAssignment assignment = assignmentRepository
                .findById(request.assignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeePeriodicAssignment not found: " + request.assignmentId()));
        if (!assignment.getEmployeeId().equals(employee.getId())) {
            throw new ConflictException(
                    "ASSIGNMENT_NOT_FOR_EMPLOYEE",
                    "Assignment " + assignment.getId()
                            + " does not belong to employee " + employee.getId(),
                    Map.of("assignmentId", assignment.getId(),
                            "employeeId", employee.getId()));
        }
        if (!Boolean.TRUE.equals(assignment.getInEffect())) {
            throw new ConflictException(
                    "ASSIGNMENT_NOT_IN_EFFECT",
                    "Assignment " + assignment.getId() + " is not in effect",
                    Map.of("assignmentId", assignment.getId()));
        }
        if (!covers(assignment, period)) {
            throw new ConflictException(
                    "ASSIGNMENT_DOES_NOT_COVER_PERIOD",
                    "Assignment " + assignment.getId()
                            + " does not cover period " + period.getName(),
                    Map.of("assignmentId", assignment.getId(),
                            "periodId", period.getId(), "periodName", period.getName()));
        }
        summaryRepository.findByEmployeeIdAndPayrollPeriodId(
                        employee.getId(), period.getId())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "SUMMARY_ALREADY_EXISTS",
                            "A payroll summary already exists for employee "
                                    + employee.getMatricule() + " and period " + period.getName(),
                            Map.of("employeeId", employee.getId(),
                                    "employeeMatricule", employee.getMatricule(),
                                    "periodId", period.getId(),
                                    "periodName", period.getName(),
                                    "summaryId", existing.getId()));
                });

        Amounts amounts = buildAmounts(
                request.base(), request.variable(), request.allowances(),
                request.employerTaxes(), request.total(), request.currency());

        PayrollSummary created = summaryRepository.save(PayrollSummary.builder()
                .employeeId(employee.getId())
                .payrollPeriod(period)
                .employeePeriodicAssignmentId(assignment.getId())
                .amounts(amounts)
                .status(PayrollSummaryStatus.DRAFT)
                .build());

        return mapper.toDetail(created, employee,
                assignment.getCountry(), assignment.getDepartment(),
                assignment.getRole(), assignment);
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @Transactional
    public PayrollSummaryDetailDto update(UUID id, PayrollSummaryUpdateRequest request) {
        PayrollSummary existing = summaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollSummary not found: " + id));
        if (existing.getStatus() != PayrollSummaryStatus.DRAFT) {
            throw new ConflictException(
                    "SUMMARY_NOT_DRAFT",
                    "Only DRAFT payroll summaries can be edited",
                    Map.of("summaryId", id, "status", existing.getStatus()));
        }

        PayrollPeriod period = periodRepository
                .findById(existing.getPayrollPeriod().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollPeriod not found: " + existing.getPayrollPeriod().getId()));
        if (Boolean.TRUE.equals(period.getClosed())) {
            throw new ConflictException(
                    "PERIOD_CLOSED",
                    "Payroll period " + period.getName() + " is closed",
                    Map.of("periodId", period.getId(), "periodName", period.getName()));
        }

        PayrollSummary updated = existing;

        // Assignment change?
        if (!request.assignmentId().equals(existing.getEmployeePeriodicAssignmentId())) {
            EmployeePeriodicAssignment newAssignment = assignmentRepository
                    .findById(request.assignmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "EmployeePeriodicAssignment not found: " + request.assignmentId()));
            if (!newAssignment.getEmployeeId().equals(existing.getEmployeeId())) {
                throw new ConflictException(
                        "ASSIGNMENT_NOT_FOR_EMPLOYEE",
                        "Assignment " + newAssignment.getId()
                                + " does not belong to employee " + existing.getEmployeeId(),
                        Map.of("assignmentId", newAssignment.getId(),
                                "employeeId", existing.getEmployeeId()));
            }
            if (!Boolean.TRUE.equals(newAssignment.getInEffect())) {
                throw new ConflictException(
                        "ASSIGNMENT_NOT_IN_EFFECT",
                        "Assignment " + newAssignment.getId() + " is not in effect",
                        Map.of("assignmentId", newAssignment.getId()));
            }
            if (!covers(newAssignment, period)) {
                throw new ConflictException(
                        "ASSIGNMENT_DOES_NOT_COVER_PERIOD",
                        "Assignment " + newAssignment.getId()
                                + " does not cover period " + period.getName(),
                        Map.of("assignmentId", newAssignment.getId(),
                                "periodId", period.getId(),
                                "periodName", period.getName()));
            }
            updated = updated.withEmployeePeriodicAssignmentId(newAssignment.getId());
        }

        Amounts amounts = buildAmounts(
                request.base(), request.variable(), request.allowances(),
                request.employerTaxes(), request.total(), request.currency());
        updated = updated.withAmounts(amounts);

        PayrollSummary saved = summaryRepository.save(updated);
        return toDetail(saved);
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @Transactional
    public void delete(UUID id) {
        PayrollSummary existing = summaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollSummary not found: " + id));
        if (existing.getStatus() != PayrollSummaryStatus.DRAFT) {
            throw new ConflictException(
                    "SUMMARY_NOT_DRAFT",
                    "Only DRAFT payroll summaries can be deleted",
                    Map.of("summaryId", id, "status", existing.getStatus()));
        }
        summaryRepository.deleteById(id);
    }

    // ------------------------------------------------------------------
    // Validate — single
    // ------------------------------------------------------------------

    public PayrollSummaryDetailDto validate(UUID id) {
        return validator.validate(id);
    }

    // ------------------------------------------------------------------
    // Validate — bulk
    // ------------------------------------------------------------------

    /**
     * Validates every {@code DRAFT} summary in the requested period that matches
     * the request's assignment filters.
     *
     * <p>Reads flat validation rows in keyset-paginated batches (see
     * {@link PayrollSummaryRepository#findValidationRows}), applies
     * {@link PayrollSummaryValidator#check} in memory, and applies a single guarded
     * bulk update per batch. The happy path costs two queries per batch (one
     * projection + one update); a third reconciliation query runs only when some
     * rows lost a race between the projection and the update.</p>
     *
     * <p>Concurrent validation / deletion is treated as a per-row
     * {@code SUMMARY_NOT_DRAFT} failure — the summary did not end up
     * {@code VALIDATED} through this call, so it is reported even though the goal
     * (the row is no longer {@code DRAFT}) may have been achieved elsewhere.</p>
     */
    @Transactional
    public ValidateAllResult validateAll(PayrollSummaryValidateRequest request) {
        if(request.rangeStart() == null || request.rangeEnd() == null){
            throw new IllegalArgumentException("A date range must be selected to validate bulk summaries");
        }

        Set<UUID> countryIds         = request.anyCountryIds();
        Set<UUID> departmentIds      = request.anyDepartmentIds();
        Set<UUID> roleIds            = request.anyRoleIds();
        Set<EmploymentType> empTypes = request.anyEmploymentTypes();

        int total = 0;
        int validated = 0;
        List<ValidateAllResult.Failure> failures = new ArrayList<>();
        UUID cursor = null;

        while (true) {
            DataPage<SummaryValidationRow> page = summaryRepository.findValidationRows(
                    request.rangeStart(),
                    request.rangeEnd(),
                    request.employeeId(),
                    countryIds, departmentIds, roleIds, empTypes,
                    request.payrollFrequency(),
                    cursor,
                    0,                               // keyset: always first page
                    BULK_PAGE_SIZE);

            if (page.content().isEmpty()) {
                break;
            }

            List<UUID> ok = new ArrayList<>(page.content().size());
            for (SummaryValidationRow row : page.content()) {
                total++;
                ValidateAllResult.Failure f = validator.check(row);
                if (f != null) {
                    failures.add(f);
                } else {
                    ok.add(row.summaryId());
                }
            }

            if (!ok.isEmpty()) {
                int updated = summaryRepository.bulkUpdateStatus(
                        ok, PayrollSummaryStatus.DRAFT, PayrollSummaryStatus.VALIDATED);
                if (updated == ok.size()) {
                    validated += ok.size();
                } else {
                    // Some rows lost the race between the projection and the
                    // update: re-query their current status once.
                    Set<UUID> confirmed = summaryRepository.findIdsWithStatus(
                            ok, PayrollSummaryStatus.VALIDATED);
                    validated += confirmed.size();
                    for (UUID id : ok) {
                        if (!confirmed.contains(id)) {
                            failures.add(new ValidateAllResult.Failure(
                                    id, "SUMMARY_NOT_DRAFT",
                                    "Payroll summary " + id + " is no longer in DRAFT"));
                        }
                    }
                }
            }

            // Advance the keyset cursor. Every returned row is seen exactly once
            // regardless of success or failure, so failures do not need an
            // excluded set and there is no skip-a-row risk under shrinking sets.
            cursor = page.content().get(page.content().size() - 1).summaryId();
        }

        return new ValidateAllResult(total, validated, failures.size(), failures);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private PayrollSummaryDto toDto(PayrollSummary summary) {
        Employee employee = employeeRepository.findById(summary.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + summary.getEmployeeId()));
        EmployeePeriodicAssignment assignment = assignmentRepository
                .findById(summary.getEmployeePeriodicAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeePeriodicAssignment not found: "
                                + summary.getEmployeePeriodicAssignmentId()));
        return mapper.toDto(summary, employee,
                assignment.getCountry(), assignment.getDepartment(),
                assignment.getRole(),
                assignment,
                assignment.getEmploymentType());
    }

    private PayrollSummaryDetailDto toDetail(PayrollSummary summary) {
        Employee employee = employeeRepository.findById(summary.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + summary.getEmployeeId()));
        EmployeePeriodicAssignment assignment = assignmentRepository
                .findById(summary.getEmployeePeriodicAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeePeriodicAssignment not found: "
                                + summary.getEmployeePeriodicAssignmentId()));
        return mapper.toDetail(summary, employee,
                assignment.getCountry(), assignment.getDepartment(),
                assignment.getRole(), assignment);
    }

    private boolean covers(EmployeePeriodicAssignment a, PayrollPeriod p) {
        if (a.getStartDate().isAfter(p.getStartDate())) return false;
        return a.getEndDate() == null || !a.getEndDate().isBefore(p.getEndDate());
    }

    private Amounts buildAmounts(
            BigDecimal base, BigDecimal variable, BigDecimal allowances,
            BigDecimal employerTaxes, BigDecimal total, String currency) {
        return Amounts.builder()
                .base(base)
                .variable(variable)
                .allowances(allowances)
                .employerTaxes(employerTaxes)
                .total(total)
                .currency(currency)
                .build();
    }

    private PayrollSummaryCriteria toCriteria(PayrollSummarySearchRequest request) {
        return PayrollSummaryCriteria.builder()
                .payrollPeriodId(null)
                .rangeStart(request.rangeStart())
                .rangeEnd(request.rangeEnd())
                .employeeId(request.employeeId())
                .employeeMatricule(request.employeeMatricule())
                .anyCountryIds(request.anyCountryIds())
                .anyDepartmentIds(request.anyDepartmentIds())
                .anyRoleIds(request.anyRoleIds())
                .anyEmploymentTypes(request.anyEmploymentTypes())
                .payrollFrequency(request.payrollFrequency())
                .nonExistent(request.nonExistent())
                .status(request.status())
                .sortBy(request.sortBy())
                .sortDirection(request.sortDirection())
                .page(request.page())
                .size(request.size())
                .build();
    }
}