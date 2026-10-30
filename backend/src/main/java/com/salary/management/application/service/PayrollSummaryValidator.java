package com.salary.management.application.service;

import com.salary.management.application.dto.payroll.PayrollSummaryDetailDto;
import com.salary.management.application.dto.payroll.ValidateAllResult;
import com.salary.management.application.mapper.PayrollSummaryApiMapper;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.repository.*;
import com.salary.management.infrastructure.persistence.projection.SummaryValidationRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Validates one payroll summary in its own transaction. Extracted into a
 * separate bean so that {@link PayrollSummaryService#validateAll} can rely on
 * {@link Propagation#REQUIRES_NEW}: successful rows commit, failed rows roll
 * back without poisoning the loop.
 */
@Component
@RequiredArgsConstructor
public class PayrollSummaryValidator {

    private final PayrollSummaryRepository summaryRepository;
    private final PayrollPeriodRepository periodRepository;
    private final EmployeePeriodicAssignmentRepository assignmentRepository;
    private final EmployeeRepository employeeRepository;
    private final CountryRepository countryRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final PayrollSummaryApiMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayrollSummaryDetailDto validate(UUID summaryId) {
        PayrollSummary summary = summaryRepository.findById(summaryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollSummary not found: " + summaryId));

        if (summary.getStatus() == PayrollSummaryStatus.VALIDATED) {
            return toDetail(summary);
        }

        PayrollPeriod period = periodRepository
                .findById(summary.getPayrollPeriod().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "PayrollPeriod not found: " + summary.getPayrollPeriod().getId()));
        if (Boolean.TRUE.equals(period.getClosed())) {
            throw new ConflictException(
                    "PERIOD_CLOSED",
                    "Payroll period " + period.getId() + " is closed",
                    Map.of("periodId", period.getId()));
        }

        EmployeePeriodicAssignment assignment = assignmentRepository
                .findById(summary.getEmployeePeriodicAssignmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "EmployeePeriodicAssignment not found: "
                                + summary.getEmployeePeriodicAssignmentId()));

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

        int updated = summaryRepository.updateStatus(
                summaryId, PayrollSummaryStatus.DRAFT, PayrollSummaryStatus.VALIDATED);
        if (updated == 0) {
            throw new ConflictException(
                    "SUMMARY_NOT_DRAFT",
                    "Payroll summary " + summaryId + " is no longer in DRAFT",
                    Map.of("summaryId", summaryId));
        }
        // Reload to return the current row (with the new status).
        PayrollSummary validated = summary.withStatus(PayrollSummaryStatus.VALIDATED);
        return toDetail(validated);
    }

    private boolean covers(EmployeePeriodicAssignment a, PayrollPeriod p) {
        return covers(a.getStartDate(), a.getEndDate(), p.getStartDate(), p.getEndDate());
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
        Country country = countryRepository.findById(assignment.getCountry().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Country not found: " + assignment.getCountry().getId()));
        Department department = departmentRepository.findById(assignment.getDepartment().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Department not found: " + assignment.getDepartment().getId()));
        Role role = roleRepository.findById(assignment.getRole().getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role not found: " + assignment.getRole().getId()));
        return mapper.toDetail(summary, employee, country, department, role, assignment);
    }

    /**
     * Pure predicate over a flat validation row. No I/O. Returns {@code null} when
     * the summary is safe to validate, or a per-row failure otherwise.
     *
     * <p>Same rules as {@link #validate(UUID)}, but operating on
     * {@link SummaryValidationRow} so the bulk path does not need to load
     * entities.</p>
     */
    public ValidateAllResult.Failure check(SummaryValidationRow row) {
        if (row.status() != PayrollSummaryStatus.DRAFT) {
            return new ValidateAllResult.Failure(row.summaryId(), "SUMMARY_NOT_DRAFT",
                    "Payroll summary " + row.summaryId() + " is not DRAFT");
        }
        if (row.assignmentId() == null) {
            return new ValidateAllResult.Failure(row.summaryId(), "ASSIGNMENT_NOT_FOUND",
                    "Assignment referenced by summary " + row.summaryId() + " does not exist");
        }
        if (Boolean.TRUE.equals(row.periodClosed())) {
            return new ValidateAllResult.Failure(row.summaryId(), "PERIOD_CLOSED",
                    "Payroll period " + row.periodId() + " is closed");
        }
        if (!Boolean.TRUE.equals(row.assignmentInEffect())) {
            return new ValidateAllResult.Failure(row.summaryId(), "ASSIGNMENT_NOT_IN_EFFECT",
                    "Assignment " + row.assignmentId() + " is not in effect");
        }
        if (!covers(row.assignmentStart(), row.assignmentEnd(),
                row.periodStart(), row.periodEnd())) {
            return new ValidateAllResult.Failure(row.summaryId(),
                    "ASSIGNMENT_DOES_NOT_COVER_PERIOD",
                    "Assignment " + row.assignmentId()
                            + " does not cover period " + row.periodId());
        }
        return null;
    }

    /** Null-safe date overlap predicate; shared by both paths. */
    private boolean covers(LocalDate aStart, LocalDate aEnd,
                           LocalDate pStart, LocalDate pEnd) {
        if (aStart.isAfter(pStart)) return false;
        return aEnd == null || !aEnd.isBefore(pEnd);
    }
}