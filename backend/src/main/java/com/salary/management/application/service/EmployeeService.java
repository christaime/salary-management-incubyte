package com.salary.management.application.service;

import com.salary.management.application.dto.employee.*;
import com.salary.management.application.mapper.EmployeeApiMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.repository.CountryRepository;
import com.salary.management.domain.repository.DepartmentRepository;
import com.salary.management.domain.repository.EmployeePeriodicAssignmentRepository;
import com.salary.management.domain.repository.EmployeeRepository;
import com.salary.management.domain.repository.PayrollSummaryRepository;
import com.salary.management.domain.repository.RoleRepository;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reads and manages employee data. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeePeriodicAssignmentRepository assignmentRepository;
    private final PayrollSummaryRepository payrollSummaryRepository;
    private final CountryRepository countryRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final EmployeeApiMapper mapper;

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DataPage<EmployeeWithInEffectAssignmentDto> page(EmployeeCriteria criteria) {
        log.debug("Employee page requested: page={}, size={}, sortBy={} {}, keyword={}, matricule={}",
                criteria.page(), criteria.size(),
                criteria.sortBy(), criteria.sortDirection(),
                criteria.keyword(), criteria.matricule());

        DataPage<Employee> employees = employeeRepository.page(criteria);

        List<EmployeeWithInEffectAssignmentDto> content = employees.content().stream()
                .map(this::toDtoWithAssignment)
                .toList();

        log.debug("Employee page returned: {} row(s) of {} total (page {} of {})",
                content.size(), employees.totalElements(),
                employees.page(), employees.totalPages());

        return new DataPage<>(
                content,
                employees.page(),
                employees.size(),
                employees.totalElements());
    }

    @Transactional(readOnly = true)
    public Optional<EmployeeWithInEffectAssignmentDto> findById(UUID id) {
        return employeeRepository.findById(id).map(this::toDtoWithAssignmentForDetail);
    }

    // ------------------------------------------------------------------
    // Create — assignment is mandatory, always new
    // ------------------------------------------------------------------

    @Transactional
    public EmployeeWithInEffectAssignmentDto create(EmployeeSaveRequest request) {
        validateForCreate(request);

        log.info("Creating employee: matricule={}, email={}",
                request.matricule(), request.email());

        Employee employee = employeeRepository.save(Employee.builder()
                .matricule(request.matricule())
                .name(request.name())
                .email(request.email())
                .birthDate(request.birthDate())
                .originCountry(resolveOriginCountry(request.originCountryId()))
                .build());

        EmployeePeriodicAssignment assignment = createAssignment(
                employee.getId(), request.assignmentInEffect());

        log.info("Employee created: id={}, assignmentId={}",
                employee.getId(), assignment.getId());

        return mapper.toDto(employee, assignment);
    }

    // ------------------------------------------------------------------
    // Update — assignments handled by payload
    // ------------------------------------------------------------------

    @Transactional
    public EmployeeWithInEffectAssignmentDto update(UUID id, EmployeeSaveRequest request) {
        Employee existing = employeeRepository.findById(id)
                .orElseThrow(() -> notFound("Employee", id));
        validateForUpdate(existing, request);

        log.info("Updating employee: id={}, matricule={}, email={}, "
                        + "hasAssignmentPayload={}, hasClosePayload={}",
                id, request.matricule(), request.email(),
                request.assignmentInEffect() != null,
                request.toClose() != null);

        Employee updated = employeeRepository.save(existing
                .withName(request.name())
                .withEmail(request.email())
                .withOriginCountry(resolveOriginCountry(request.originCountryId())));

        // 1. Close first, so the old assignment is retired before the new one
        //    is created. Only runs when the caller explicitly sent toClose.
        if (request.toClose() != null) {
            closeAssignment(request.toClose());
        }

        // 2. Create or update the assignment.
        //    - If no payload: do nothing. An existing in-effect assignment stays.
        //    - If id == null: create a new in-effect assignment. This is only
        //      allowed if no other in-effect assignment remains (which, after
        //      step 1, means the caller must have sent toClose for the old one).
        //    - If id != null: update that assignment in place.
        if (request.assignmentInEffect() != null) {
            EmployeeAssignmentSaveRequest save = request.assignmentInEffect();
            if (save.id() == null) {
                rejectIfInEffectNotBeingClosed(updated.getId(), request.toClose());
                EmployeePeriodicAssignment created = createAssignment(updated.getId(), save);
                log.info("Employee updated, new assignment created: id={}, assignmentId={}",
                        updated.getId(), created.getId());
            } else {
                updateAssignment(save);
                log.info("Employee updated, assignment updated: id={}, assignmentId={}",
                        updated.getId(), save.id());
            }
        }

        EmployeePeriodicAssignment inEffect =
                findInEffectAssignment(updated.getId()).orElse(null);
        return mapper.toDto(updated, inEffect);
    }

    // ------------------------------------------------------------------
    // Delete — remove assignments first, then employee
    // ------------------------------------------------------------------

    @Transactional
    public void delete(UUID id) {
        Employee existing = employeeRepository.findById(id)
                .orElseThrow(() -> notFound("Employee", id));

        log.info("Deleting employee: id={}, assignments={}", id,
                assignmentRepository.findByEmployeeId(id).size());

        // A referenced assignment throws DataIntegrityViolationException here,
        // before the employee is touched.
        for (EmployeePeriodicAssignment a : assignmentRepository.findByEmployeeId(id)) {
            assignmentRepository.deleteById(a.getId());
        }
        employeeRepository.deleteById(id);

        log.info("Employee deleted: id={}", id);
    }

    // ------------------------------------------------------------------
    // Assignment operations
    // ------------------------------------------------------------------

    /** Creates a new in-effect assignment for the employee. */
    private EmployeePeriodicAssignment createAssignment(
            UUID employeeId, EmployeeAssignmentSaveRequest save) {
        EmployeePeriodicAssignment assignment = EmployeePeriodicAssignment.builder()
                .employeeId(employeeId)
                .startDate(save.startDate())
                .endDate(save.endDate())
                .country(resolveCountry(save.countryId()))
                .department(resolveDepartment(save.departmentId()))
                .role(resolveRole(save.roleId()))
                .employmentType(save.employmentType())
                .payrollFrequency(save.payrollFrequency())
                .inEffect(true)
                .build();
        return assignmentRepository.save(assignment);
    }

    /**
     * Updates an assignment in place.
     *
     * <p>Guards:</p>
     * <ul>
     *   <li>{@code endDate} must not be before the latest linked payroll
     *       period's {@code startDate}.</li>
     *   <li>Once a payroll summary is linked, only {@code endDate} and
     *       {@code inEffect} are mutable.</li>
     * </ul>
     */
    private EmployeePeriodicAssignment updateAssignment(EmployeeAssignmentSaveRequest save) {
        EmployeePeriodicAssignment existing = assignmentRepository.findById(save.id())
                .orElseThrow(() -> notFound("Assignment", save.id()));

        rejectIfEndDateTooEarly(existing.getId(), save.endDate());

        boolean structuralChange =
                !existing.getCountry().getId().equals(save.countryId())
                        || !existing.getDepartment().getId().equals(save.departmentId())
                        || !existing.getRole().getId().equals(save.roleId())
                        || existing.getEmploymentType() != save.employmentType()
                        || existing.getPayrollFrequency() != save.payrollFrequency()
                        || !existing.getStartDate().equals(save.startDate());

        if (structuralChange
                && payrollSummaryRepository.latestLinkedPeriodStart(existing.getId()) != null) {
            throw new DomainValidationException(List.of(new ValidationError(
                    "assignmentInEffect",
                    "ImmutableAfterPayroll",
                    "Only endDate and inEffect may be changed once a payroll summary is linked",
                    Map.of("assignmentId", existing.getId()))));
        }

        EmployeePeriodicAssignment updated = existing
                .withCountry(resolveCountry(save.countryId()))
                .withDepartment(resolveDepartment(save.departmentId()))
                .withRole(resolveRole(save.roleId()))
                .withPayrollFrequency(save.payrollFrequency())
                .withEmploymentType(save.employmentType())
                .withEndDate(save.endDate());
        // Note: employmentType and startDate have no withX on the domain; add
        // them if you want to allow structural changes before payroll exists.
        return assignmentRepository.save(updated);
    }

    /**
     * Closes an assignment: sets {@code endDate} and {@code in_effect = false}.
     *
     * <p>Guards: {@code endDate} after the assignment's {@code startDate} and
     * not before the latest linked payroll period's {@code startDate}.</p>
     */
    private EmployeePeriodicAssignment closeAssignment(EmployeeAssignmentToCloseRequest toClose) {
        EmployeePeriodicAssignment existing = assignmentRepository.findById(toClose.id())
                .orElseThrow(() -> notFound("Assignment", toClose.id()));

        rejectIfEndDateTooEarly(existing.getId(), toClose.endDate());

        if (!toClose.endDate().isAfter(existing.getStartDate())) {
            throw new DomainValidationException(List.of(new ValidationError(
                    "toClose.endDate",
                    "DateOrder",
                    "endDate must be after the assignment's startDate",
                    Map.of("startDate", existing.getStartDate()))));
        }

        EmployeePeriodicAssignment closed = existing
                .withEndDate(toClose.endDate())
                .withInEffect(false);
        return assignmentRepository.save(closed);
    }

    // ------------------------------------------------------------------
    // Guards
    // ------------------------------------------------------------------

    private void rejectIfEndDateTooEarly(UUID assignmentId, LocalDate newEndDate) {
        if (newEndDate == null) {
            return;
        }
        LocalDate latestLinkedStart = payrollSummaryRepository.latestLinkedPeriodStart(assignmentId);
        if (latestLinkedStart != null && newEndDate.isBefore(latestLinkedStart)) {
            throw new DomainValidationException(List.of(new ValidationError(
                    "endDate",
                    "BeforeLinkedPeriod",
                    "endDate cannot be before the latest linked payroll period's startDate",
                    Map.of(
                            "endDate", newEndDate,
                            "latestLinkedPeriodStart", latestLinkedStart))));
        }
    }

    /**
     * Guards the creation of a new in-effect assignment: there must be no
     * in-effect assignment left for the employee other than the one being
     * closed by {@code toClose}.
     *
     * <p>Called after {@code toClose} has already been processed, so any
     * assignment the caller asked to close has already been flipped to
     * {@code in_effect = false}. If this method still finds an in-effect
     * assignment, it means the caller did not close it, and creating a new
     * one would result in two in-effect assignments for the same employee.</p>
     */
    private void rejectIfInEffectNotBeingClosed(
            UUID employeeId, EmployeeAssignmentToCloseRequest toClose) {
        UUID closingId = toClose == null ? null : toClose.id();
        boolean stillInEffect = assignmentRepository
                .list(EmployeePeriodicAssignmentCriteria.builder()
                        .employeeId(employeeId)
                        .inEffect(true)
                        .build())
                .stream()
                .anyMatch(a -> !a.getId().equals(closingId));

        if (stillInEffect) {
            throw new DomainValidationException(List.of(new ValidationError(
                    "assignmentInEffect",
                    "InEffectNotClosed",
                    "Cannot create a new in-effect assignment while another one is "
                            + "still in effect; send toClose for the existing one",
                    Map.of("employeeId", employeeId))));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private EmployeeWithInEffectAssignmentDto toDtoWithAssignment(Employee employee) {
        EmployeePeriodicAssignment inEffect = findInEffectAssignment(employee.getId()).orElse(null);
        return mapper.toDto(employee, inEffect);
    }

    /**
     * Builds the DTO for a single-employee read, including the
     * {@code editable} flag on the assignment.
     */
    private EmployeeWithInEffectAssignmentDto toDtoWithAssignmentForDetail(Employee employee) {
        EmployeePeriodicAssignment inEffect = findInEffectAssignment(employee.getId()).orElse(null);

        EmployeeWithInEffectAssignmentDto base = mapper.toDto(employee, inEffect);
        if (base.assignmentInEffect() == null) {
            return base;
        }

        boolean hasPayroll = payrollSummaryRepository.hasAnySummaryForAssignment(inEffect.getId());

        // Rebuild the DTO with the computed editable flag.
        // (Records are immutable; the builder is the cleanest way to set one field.)
        EmployeeAssignmentDto annotated = EmployeeAssignmentDto.builder()
                .id(base.assignmentInEffect().id())
                .startDate(base.assignmentInEffect().startDate())
                .endDate(base.assignmentInEffect().endDate())
                .country(base.assignmentInEffect().country())
                .department(base.assignmentInEffect().department())
                .role(base.assignmentInEffect().role())
                .employmentType(base.assignmentInEffect().employmentType())
                .payrollFrequency(base.assignmentInEffect().payrollFrequency())
                .hasPayroll(hasPayroll)
                .build();

        return EmployeeWithInEffectAssignmentDto.builder()
                .id(base.id())
                .matricule(base.matricule())
                .name(base.name())
                .email(base.email())
                .birthDate(base.birthDate())
                .originCountry(base.originCountry())
                .assignmentInEffect(annotated)
                .build();
    }

    private Optional<EmployeePeriodicAssignment> findInEffectAssignment(UUID employeeId) {
        return assignmentRepository
                .list(EmployeePeriodicAssignmentCriteria.builder()
                        .employeeId(employeeId)
                        .inEffect(true)
                        .build())
                .stream()
                .findFirst();
    }

    private Country resolveCountry(UUID id) {
        return countryRepository.findById(id)
                .orElseThrow(() -> notFound("Country", id));
    }

    private Department resolveDepartment(UUID id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> notFound("Department", id));
    }

    private Role resolveRole(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> notFound("Role", id));
    }

    private Country resolveOriginCountry(UUID id) {
        return id == null ? null : resolveCountry(id);
    }

    private DomainValidationException notFound(String entity, UUID id) {
        return new DomainValidationException(List.of(new ValidationError(
                "id", "NotFound", entity + " not found: " + id, Map.of())));
    }

    // ------------------------------------------------------------------
    // Validation entry points
    //
    // The DTO already enforces field-level rules via Jakarta Bean Validation
    // (@NotBlank, @NotNull, @Email). Only cross-field and repository-dependent
    // rules are checked here.
    // ------------------------------------------------------------------

    private void validateForCreate(EmployeeSaveRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        if (request.id() != null) {
            errors.add(new ValidationError("id", "NotAllowed",
                    "id must be null on create", Map.of()));
        }
        if (employeeRepository.existsByMatricule(request.matricule())) {
            errors.add(new ValidationError("matricule", "Conflict",
                    "matricule already in use: " + request.matricule(), Map.of()));
        }
        if (request.assignmentInEffect() == null) {
            errors.add(new ValidationError("assignmentInEffect", "Required",
                    "an employee must be created with an in-effect assignment", Map.of()));
        } else if (request.assignmentInEffect().id() != null) {
            errors.add(new ValidationError("assignmentInEffect.id", "NotAllowed",
                    "assignmentInEffect.id must be null on create", Map.of()));
        }
        if (request.toClose() != null) {
            errors.add(new ValidationError("toClose", "NotAllowed",
                    "toClose is not accepted on create", Map.of()));
        }
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }

    private void validateForUpdate(Employee existing, EmployeeSaveRequest request) {
        List<ValidationError> errors = new ArrayList<>();
        if (!request.matricule().equals(existing.getMatricule())
                && employeeRepository.existsByMatricule(request.matricule())) {
            errors.add(new ValidationError("matricule", "Conflict",
                    "matricule already in use: " + request.matricule(), Map.of()));
        }
        if (request.toClose() != null
                && request.assignmentInEffect() != null
                && request.assignmentInEffect().id() != null
                && request.toClose().id().equals(request.assignmentInEffect().id())) {
            errors.add(new ValidationError(
                    "toClose",
                    "ConflictingWithUpdate",
                    "cannot close and update the same assignment in one request",
                    Map.of("assignmentId", request.toClose().id())));
        }
        if (!errors.isEmpty()) {
            throw new DomainValidationException(errors);
        }
    }
}