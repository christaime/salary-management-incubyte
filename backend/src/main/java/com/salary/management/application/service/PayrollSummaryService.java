package com.salary.management.application.service;

import com.salary.management.application.dto.payroll.*;
import com.salary.management.application.mapper.PayrollSummaryApiMapper;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.*;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.*;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import jakarta.validation.ValidationException;
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
        DataPage<PayrollSummary> source = summaryRepository.page(criteria);
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
                    "Payroll period " + period.getId() + " is closed",
                    Map.of("periodId", period.getId()));
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
                            + " does not cover period " + period.getId(),
                    Map.of("assignmentId", assignment.getId(),
                            "periodId", period.getId()));
        }
        summaryRepository.findByEmployeeIdAndPayrollPeriodId(
                        employee.getId(), period.getId())
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "SUMMARY_ALREADY_EXISTS",
                            "A payroll summary already exists for employee "
                                    + employee.getId() + " and period " + period.getId(),
                            Map.of("employeeId", employee.getId(),
                                    "periodId", period.getId(),
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
                    "Payroll period " + period.getId() + " is closed",
                    Map.of("periodId", period.getId()));
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
                                + " does not cover period " + period.getId(),
                        Map.of("assignmentId", newAssignment.getId(),
                                "periodId", period.getId()));
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

    public ValidateAllResult validateAll(PayrollSummaryValidateRequest request) {
        periodRepository.findById(request.periodId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollPeriod not found: " + request.periodId()));

        int total = 0;
        int validated = 0;
        List<ValidateAllResult.Failure> failures = new ArrayList<>();
        List<UUID> excluded = new ArrayList<>();
        int pageIndex = 0;

        while (true) {
            if (excluded.size() >= BULK_PAGE_SIZE) {
                excluded = new ArrayList<>(
                        excluded.subList(BULK_PAGE_SIZE, excluded.size()));
                pageIndex++;
            }

            PayrollSummaryCriteria criteria = PayrollSummaryCriteria.builder()
                    .payrollPeriodId(request.periodId())
                    .status(PayrollSummaryStatus.DRAFT)
                    .anyCountryIds(request.anyCountryIds())
                    .anyDepartmentIds(request.anyDepartmentIds())
                    .anyRoleIds(request.anyRoleIds())
                    .anyEmploymentTypes(request.anyEmploymentTypes())
                    .excludeIds(new HashSet<>(excluded))
                    .page(pageIndex)
                    .size(BULK_PAGE_SIZE)
                    .build();

            DataPage<PayrollSummary> chunk = summaryRepository.page(criteria);
            if (chunk.content().isEmpty()) {
                break;
            }

            for (PayrollSummary draft : chunk.content()) {
                total++;
                try {
                    validator.validate(draft.getId());
                    validated++;
                } catch (ConflictException ex) {
                    failures.add(new ValidateAllResult.Failure(
                            draft.getId(), ex.getCode(), ex.getMessage()));
                    excluded.add(draft.getId());
                } catch (RuntimeException ex) {
                    log.error("Unexpected failure validating payroll summary {}",
                            draft.getId(), ex);
                    failures.add(new ValidateAllResult.Failure(
                            draft.getId(), "UNEXPECTED_ERROR", ex.getMessage()));
                    excluded.add(draft.getId());
                }
            }
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
                assignment.getRole(), assignment.getEmploymentType());
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
                .payrollPeriodId(null)                         // date range mode
                .rangeStart(request.rangeStart())
                .rangeEnd(request.rangeEnd())
                .employeeId(request.employeeId())
                .employeeMatricule(request.employeeMatricule())
                .anyCountryIds(request.anyCountryIds())
                .anyDepartmentIds(request.anyDepartmentIds())
                .anyRoleIds(request.anyRoleIds())
                .anyEmploymentTypes(request.anyEmploymentTypes())
                .status(request.status())
                .sortBy(request.sortBy())
                .sortDirection(request.sortDirection())
                .page(request.page())
                .size(request.size())
                .build();
    }
}