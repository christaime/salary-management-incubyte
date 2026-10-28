package com.salary.management.application.service;

import com.salary.management.application.dto.RefDto;
import com.salary.management.application.dto.employee.EmployeeAssignmentDto;
import com.salary.management.application.dto.employee.EmployeeAssignmentSaveRequest;
import com.salary.management.application.dto.employee.EmployeeAssignmentToCloseRequest;
import com.salary.management.application.dto.employee.EmployeeSaveRequest;
import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.application.mapper.EmployeeApiMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.CountryRepository;
import com.salary.management.domain.repository.DepartmentRepository;
import com.salary.management.domain.repository.EmployeePeriodicAssignmentRepository;
import com.salary.management.domain.repository.EmployeeRepository;
import com.salary.management.domain.repository.PayrollSummaryRepository;
import com.salary.management.domain.repository.RoleRepository;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeePeriodicAssignmentRepository assignmentRepository;

    @Mock
    private PayrollSummaryRepository payrollSummaryRepository;

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EmployeeApiMapper mapper;

    @InjectMocks
    private EmployeeService service;

    private UUID FR;
    private UUID DE;
    private UUID ENGINEERING;
    private UUID FINANCE;
    private UUID ENGINEER;
    private UUID MANAGER;
    private UUID EMPLOYEE_ID;
    private UUID ASSIGNMENT_1;
    private UUID ASSIGNMENT_2;

    private Employee employee;
    private EmployeePeriodicAssignment assignment;
    private EmployeeWithInEffectAssignmentDto dto;

    @BeforeEach
    void setup() {
        FR = UUID.randomUUID();
        DE = UUID.randomUUID();
        ENGINEERING = UUID.randomUUID();
        FINANCE = UUID.randomUUID();
        ENGINEER = UUID.randomUUID();
        MANAGER = UUID.randomUUID();
        EMPLOYEE_ID = UUID.randomUUID();
        ASSIGNMENT_1 = UUID.randomUUID();
        ASSIGNMENT_2 = UUID.randomUUID();

        employee = Employee.builder()
                .id(EMPLOYEE_ID)
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
        assignment = EmployeePeriodicAssignment.builder()
                .id(ASSIGNMENT_1)
                .employeeId(EMPLOYEE_ID)
                .startDate(LocalDate.of(2025, 10, 1))
                .country(country(FR))
                .department(department(ENGINEERING))
                .role(role(ENGINEER))
                .employmentType(EmploymentType.FULL_TIME)
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY)
                .inEffect(true)
                .build();
        dto = EmployeeWithInEffectAssignmentDto.builder()
                .id(EMPLOYEE_ID)
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
    }

    // ------------------------------------------------------------------
    // Page
    // ------------------------------------------------------------------

    @Test
    @DisplayName("page returns the mapped content of the underlying DataPage")
    void pageMapsContent() {
        when(employeeRepository.page(any(EmployeeCriteria.class)))
                .thenReturn(new DataPage<>(List.of(employee), 0, 20, 1));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(assignment));
        when(mapper.toDto(employee, assignment)).thenReturn(dto);

        DataPage<EmployeeWithInEffectAssignmentDto> result = service.page(criteria());

        assertEquals(1, result.content().size());
        assertEquals(0, result.page());
        assertEquals(20, result.size());
        assertEquals(1, result.totalElements());
        assertEquals(employee.getId(), result.content().get(0).id());
    }

    @Test
    @DisplayName("page looks up the in-effect assignment per employee")
    void pageLooksUpInEffectAssignment() {
        when(employeeRepository.page(any(EmployeeCriteria.class)))
                .thenReturn(new DataPage<>(List.of(employee), 0, 20, 1));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(assignment));
        when(mapper.toDto(employee, assignment)).thenReturn(dto);

        service.page(criteria());

        verify(assignmentRepository).list(any(EmployeePeriodicAssignmentCriteria.class));
        verify(mapper).toDto(employee, assignment);
    }

    @Test
    @DisplayName("page builds the assignment criteria with the employee id and inEffect = true")
    void pageBuildsCriteriaWithEmployeeAndInEffect() {
        when(employeeRepository.page(any(EmployeeCriteria.class)))
                .thenReturn(new DataPage<>(List.of(employee), 0, 20, 1));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(assignment));
        when(mapper.toDto(employee, assignment)).thenReturn(dto);

        service.page(criteria());

        ArgumentCaptor<EmployeePeriodicAssignmentCriteria> captor =
                ArgumentCaptor.forClass(EmployeePeriodicAssignmentCriteria.class);
        verify(assignmentRepository).list(captor.capture());

        EmployeePeriodicAssignmentCriteria captured = captor.getValue();
        assertEquals(employee.getId(), captured.employeeId());
        assertEquals(Boolean.TRUE, captured.inEffect());
    }

    @Test
    @DisplayName("page passes null to the mapper when no in-effect assignment exists")
    void pagePassesNullWhenNoAssignment() {
        when(employeeRepository.page(any(EmployeeCriteria.class)))
                .thenReturn(new DataPage<>(List.of(employee), 0, 20, 1));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of());
        when(mapper.toDto(employee, null)).thenReturn(
                EmployeeWithInEffectAssignmentDto.builder()
                        .id(employee.getId())
                        .matricule("EMP-0001")
                        .name("Ada Lovelace")
                        .email("ada@example.com")
                        .assignmentInEffect(null)
                        .build());

        DataPage<EmployeeWithInEffectAssignmentDto> result = service.page(criteria());

        assertEquals(1, result.content().size());
        assertNull(result.content().get(0).assignmentInEffect());
        verify(assignmentRepository).list(any(EmployeePeriodicAssignmentCriteria.class));
        verify(mapper).toDto(employee, null);
    }

    @Test
    @DisplayName("page returns empty content when the repository page is empty")
    void pageEmpty() {
        when(employeeRepository.page(any(EmployeeCriteria.class)))
                .thenReturn(new DataPage<>(List.of(), 0, 20, 0));

        DataPage<EmployeeWithInEffectAssignmentDto> result = service.page(criteria());

        assertTrue(result.content().isEmpty());
        assertEquals(0, result.totalElements());
        verify(assignmentRepository, never()).list(any(EmployeePeriodicAssignmentCriteria.class));
    }

    @Test
    @DisplayName("page preserves pagination metadata from the source page")
    void pagePreservesMetadata() {
        when(employeeRepository.page(any(EmployeeCriteria.class)))
                .thenReturn(new DataPage<>(List.of(), 3, 25, 137));

        DataPage<EmployeeWithInEffectAssignmentDto> result = service.page(criteria());

        assertEquals(3, result.page());
        assertEquals(25, result.size());
        assertEquals(137, result.totalElements());
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @Test
    @DisplayName("create persists the employee and the assignment")
    void createPersistsEmployeeAndAssignment() {
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR,
                saveAssignment(null, FR, ENGINEERING, ENGINEER,
                        EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                        LocalDate.of(2026, 1, 1), null),
                null);

        Employee savedEmployee = employee();
        EmployeePeriodicAssignment savedAssignment = assignment(ASSIGNMENT_1, true);
        EmployeeWithInEffectAssignmentDto response =
                dtoWithAssignment(savedAssignment);

        when(employeeRepository.existsByMatricule("EMP-0001")).thenReturn(false);
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(departmentRepository.findById(ENGINEERING))
                .thenReturn(Optional.of(department(ENGINEERING)));
        when(roleRepository.findById(ENGINEER)).thenReturn(Optional.of(role(ENGINEER)));
        when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmployee);
        when(assignmentRepository.save(any(EmployeePeriodicAssignment.class)))
                .thenReturn(savedAssignment);
        when(mapper.toDto(savedEmployee, savedAssignment)).thenReturn(response);

        EmployeeWithInEffectAssignmentDto result = service.create(request);

        assertEquals(response, result);
        verify(employeeRepository).save(any(Employee.class));
        verify(assignmentRepository).save(any(EmployeePeriodicAssignment.class));
    }

    @Test
    @DisplayName("create rejects when matricule is already taken")
    void createRejectsDuplicateMatricule() {
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR,
                saveAssignment(null, FR, ENGINEERING, ENGINEER,
                        EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                        LocalDate.of(2026, 1, 1), null),
                null);

        when(employeeRepository.existsByMatricule("EMP-0001")).thenReturn(true);

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.create(request));
        assertEquals("Conflict", ex.getErrors().get(0).code());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects when assignmentInEffect is null")
    void createRejectsMissingAssignment() {
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR,
                null, null);

        when(employeeRepository.existsByMatricule("EMP-0001")).thenReturn(false);

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.create(request));
        assertEquals("Required", ex.getErrors().get(0).code());
        assertEquals("assignmentInEffect", ex.getErrors().get(0).field());
    }

    @Test
    @DisplayName("create rejects when assignmentInEffect.id is set")
    void createRejectsAssignmentWithId() {
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR,
                saveAssignment(ASSIGNMENT_1, FR, ENGINEERING, ENGINEER,
                        EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                        LocalDate.of(2026, 1, 1), null),
                null);

        when(employeeRepository.existsByMatricule("EMP-0001")).thenReturn(false);

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.create(request));
        assertEquals("NotAllowed", ex.getErrors().get(0).code());
        assertEquals("assignmentInEffect.id", ex.getErrors().get(0).field());
    }

    @Test
    @DisplayName("create rejects when toClose is set")
    void createRejectsToClose() {
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR,
                saveAssignment(null, FR, ENGINEERING, ENGINEER,
                        EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                        LocalDate.of(2026, 1, 1), null),
                new EmployeeAssignmentToCloseRequest(ASSIGNMENT_1, LocalDate.of(2026, 6, 30)));

        when(employeeRepository.existsByMatricule("EMP-0001")).thenReturn(false);

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.create(request));
        assertEquals("NotAllowed", ex.getErrors().get(0).code());
        assertEquals("toClose", ex.getErrors().get(0).field());
    }

    // ------------------------------------------------------------------
    // Update — happy paths
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update with no assignment payload leaves assignment untouched")
    void updateWithoutAssignmentPayloadLeavesAssignmentUntouched() {
        Employee existing = employee();
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada King", "ada.king@example.com", FR, null, null);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(assignment));
        when(mapper.toDto(any(Employee.class), eq(assignment)))
                .thenReturn(dtoWithAssignment(assignment));

        service.update(EMPLOYEE_ID, request);

        // The service persisted the employee with the new name, and passed
        // that same updated employee to the mapper.
        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(mapper).toDto(employeeCaptor.capture(), eq(assignment));
        assertEquals("Ada King", employeeCaptor.getValue().getName());
        assertEquals("ada.king@example.com", employeeCaptor.getValue().getEmail());

        // No assignment operations happened.
        verify(assignmentRepository, never()).deleteById(any());
        verify(assignmentRepository, never()).save(any());

        // The in-effect assignment is preserved in the mapper call.
        verify(mapper).toDto(any(Employee.class), eq(assignment));
    }

    @Test
    @DisplayName("update with assignmentInEffect.id == null creates a new assignment")
    void updateCreatesNewAssignmentWhenNoCurrentInEffect() {
        Employee existing = employee();
        EmployeeAssignmentSaveRequest save = saveAssignment(
                null, FR, ENGINEERING, ENGINEER,
                EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                LocalDate.of(2026, 1, 1), null);
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, save, null);

        EmployeePeriodicAssignment newAssignment = assignment(ASSIGNMENT_2, true);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(departmentRepository.findById(ENGINEERING))
                .thenReturn(Optional.of(department(ENGINEERING)));
        when(roleRepository.findById(ENGINEER)).thenReturn(Optional.of(role(ENGINEER)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        // First list: inside rejectIfInEffectNotBeingClosed, nothing in effect.
        // Second list: inside findInEffectAssignment at end of update, the new one.
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(newAssignment));
        when(assignmentRepository.save(any(EmployeePeriodicAssignment.class)))
                .thenReturn(newAssignment);
        when(mapper.toDto(any(Employee.class), eq(newAssignment)))
                .thenReturn(dtoWithAssignment(newAssignment));

        EmployeeWithInEffectAssignmentDto result = service.update(EMPLOYEE_ID, request);

        assertEquals(ASSIGNMENT_2, result.assignmentInEffect().id());
        verify(assignmentRepository).save(any(EmployeePeriodicAssignment.class));
        verify(assignmentRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("update with assignmentInEffect.id != null updates the assignment in place")
    void updateUpdatesAssignmentInPlace() {
        Employee existing = employee();
        EmployeeAssignmentSaveRequest save = saveAssignment(
                ASSIGNMENT_1, FR, ENGINEERING, ENGINEER,
                EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, save, null);

        EmployeePeriodicAssignment updated = assignment(ASSIGNMENT_1, true);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(departmentRepository.findById(ENGINEERING))
                .thenReturn(Optional.of(department(ENGINEERING)));
        when(roleRepository.findById(ENGINEER)).thenReturn(Optional.of(role(ENGINEER)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(ASSIGNMENT_1)).thenReturn(Optional.of(assignment));
        when(payrollSummaryRepository.latestLinkedPeriodStart(ASSIGNMENT_1)).thenReturn(null);
        when(assignmentRepository.save(any(EmployeePeriodicAssignment.class)))
                .thenReturn(updated);
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(updated));
        when(mapper.toDto(any(Employee.class), eq(updated)))
                .thenReturn(dtoWithAssignment(updated));

        EmployeeWithInEffectAssignmentDto result = service.update(EMPLOYEE_ID, request);

        assertEquals(ASSIGNMENT_1, result.assignmentInEffect().id());
        verify(assignmentRepository).save(any(EmployeePeriodicAssignment.class));
        verify(assignmentRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("update with toClose closes the assignment and clears in-effect")
    void updateWithToClose() {
        Employee existing = employee();
        EmployeeAssignmentToCloseRequest toClose =
                new EmployeeAssignmentToCloseRequest(ASSIGNMENT_1, LocalDate.of(2026, 6, 30));
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, null, toClose);

        EmployeePeriodicAssignment closed = assignment(ASSIGNMENT_1, false);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(ASSIGNMENT_1)).thenReturn(Optional.of(assignment));
        when(payrollSummaryRepository.latestLinkedPeriodStart(ASSIGNMENT_1)).thenReturn(null);
        when(assignmentRepository.save(any(EmployeePeriodicAssignment.class)))
                .thenReturn(closed);
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of());
        when(mapper.toDto(any(Employee.class), eq(null)))
                .thenReturn(dtoWithoutAssignment());

        EmployeeWithInEffectAssignmentDto result = service.update(EMPLOYEE_ID, request);

        assertNull(result.assignmentInEffect());
        verify(assignmentRepository).save(argThat(a ->
                a.getId().equals(ASSIGNMENT_1)
                        && !a.getInEffect()
                        && LocalDate.of(2026, 6, 30).equals(a.getEndDate())));
    }

    @Test
    @DisplayName("update with toClose and new assignment closes old and creates new")
    void updateWithToCloseAndNewAssignment() {
        Employee existing = employee();
        EmployeeAssignmentToCloseRequest toClose =
                new EmployeeAssignmentToCloseRequest(ASSIGNMENT_1, LocalDate.of(2026, 6, 30));
        EmployeeAssignmentSaveRequest save = saveAssignment(
                null, DE, ENGINEERING, ENGINEER,
                EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                LocalDate.of(2026, 7, 1), null);
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, save, toClose);

        EmployeePeriodicAssignment closed = assignment(ASSIGNMENT_1, false);
        EmployeePeriodicAssignment created = assignment(ASSIGNMENT_2, true);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(countryRepository.findById(DE)).thenReturn(Optional.of(country(DE)));
        when(departmentRepository.findById(ENGINEERING))
                .thenReturn(Optional.of(department(ENGINEERING)));
        when(roleRepository.findById(ENGINEER)).thenReturn(Optional.of(role(ENGINEER)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(ASSIGNMENT_1)).thenReturn(Optional.of(assignment));
        when(payrollSummaryRepository.latestLinkedPeriodStart(ASSIGNMENT_1)).thenReturn(null);
        when(assignmentRepository.save(any(EmployeePeriodicAssignment.class)))
                .thenReturn(closed)
                .thenReturn(created);
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(created));
        when(mapper.toDto(any(Employee.class), eq(created)))
                .thenReturn(dtoWithAssignment(created));

        EmployeeWithInEffectAssignmentDto result = service.update(EMPLOYEE_ID, request);

        assertEquals(ASSIGNMENT_2, result.assignmentInEffect().id());
        verify(assignmentRepository, times(2)).save(any(EmployeePeriodicAssignment.class));
    }

    // ------------------------------------------------------------------
    // Update — guards
    // ------------------------------------------------------------------

    @Test
    @DisplayName("update with new assignment while another is still in effect throws")
    void updateNewAssignmentWhileInEffectExistsThrows() {
        Employee existing = employee();
        EmployeeAssignmentSaveRequest save = saveAssignment(
                null, DE, ENGINEERING, ENGINEER,
                EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                LocalDate.of(2026, 7, 1), null);
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, save, null);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(assignment));

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.update(EMPLOYEE_ID, request));
        assertEquals("InEffectNotClosed", ex.getErrors().get(0).code());
        verify(assignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update with toClose and assignmentInEffect targeting the same id is rejected")
    void updateClosingAndUpdatingSameAssignmentThrows() {
        Employee existing = employee();
        EmployeeAssignmentToCloseRequest toClose =
                new EmployeeAssignmentToCloseRequest(ASSIGNMENT_1, LocalDate.of(2026, 6, 30));
        EmployeeAssignmentSaveRequest save = saveAssignment(
                ASSIGNMENT_1, FR, ENGINEERING, ENGINEER,
                EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, save, toClose);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.update(EMPLOYEE_ID, request));
        assertEquals("ConflictingWithUpdate", ex.getErrors().get(0).code());
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("update in-place with structural change on assignment that has payroll is rejected")
    void updateStructuralChangeAfterPayrollThrows() {
        Employee existing = employee();
        EmployeeAssignmentSaveRequest save = saveAssignment(
                ASSIGNMENT_1, DE, ENGINEERING, ENGINEER,
                EmploymentType.FULL_TIME, PayrollPeriodFrequency.MONTHLY,
                LocalDate.of(2026, 1, 1), null);
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, save, null);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(ASSIGNMENT_1)).thenReturn(Optional.of(assignment));
        when(payrollSummaryRepository.latestLinkedPeriodStart(ASSIGNMENT_1))
                .thenReturn(LocalDate.of(2026, 3, 1));

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.update(EMPLOYEE_ID, request));
        assertEquals("ImmutableAfterPayroll", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("update with toClose whose endDate is before the latest linked period throws")
    void updateCloseEndDateBeforeLinkedPeriodThrows() {
        Employee existing = employee();
        EmployeeAssignmentToCloseRequest toClose =
                new EmployeeAssignmentToCloseRequest(ASSIGNMENT_1, LocalDate.of(2026, 1, 15));
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, null, toClose);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(ASSIGNMENT_1)).thenReturn(Optional.of(assignment));
        when(payrollSummaryRepository.latestLinkedPeriodStart(ASSIGNMENT_1))
                .thenReturn(LocalDate.of(2026, 3, 1));

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.update(EMPLOYEE_ID, request));
        assertEquals("BeforeLinkedPeriod", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("update with toClose whose endDate is not after startDate throws")
    void updateCloseEndDateNotAfterStartDateThrows() {
        Employee existing = employee();
        EmployeeAssignmentToCloseRequest toClose =
                new EmployeeAssignmentToCloseRequest(ASSIGNMENT_1, LocalDate.of(2025, 10, 1));
        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada Lovelace", "ada@example.com", FR, null, toClose);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(countryRepository.findById(FR)).thenReturn(Optional.of(country(FR)));
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(assignmentRepository.findById(ASSIGNMENT_1)).thenReturn(Optional.of(assignment));
        when(payrollSummaryRepository.latestLinkedPeriodStart(ASSIGNMENT_1)).thenReturn(null);

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.update(EMPLOYEE_ID, request));
        assertEquals("DateOrder", ex.getErrors().get(0).code());
    }

    @Test
    @DisplayName("update throws when employee does not exist")
    void updateMissingEmployeeThrows() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        EmployeeSaveRequest request = saveRequest(
                "EMP-0001", "Ada King", "ada.king@example.com", FR, null, null);

        DomainValidationException ex = assertThrows(DomainValidationException.class,
                () -> service.update(EMPLOYEE_ID, request));
        assertEquals("NotFound", ex.getErrors().get(0).code());
    }

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    @DisplayName("findById returns hasPayroll = false when no payroll summary exists")
    void findByIdReturnsHasPayrollFalse() {
        Employee existing = employee();
        EmployeeWithInEffectAssignmentDto baseDto = dtoWithAssignment(assignment);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(assignment));
        when(mapper.toDto(existing, assignment)).thenReturn(baseDto);
        when(payrollSummaryRepository.hasAnySummaryForAssignment(ASSIGNMENT_1))
                .thenReturn(false);

        Optional<EmployeeWithInEffectAssignmentDto> result = service.findById(EMPLOYEE_ID);

        assertTrue(result.isPresent());
        assertEquals(ASSIGNMENT_1, result.get().assignmentInEffect().id());
        assertFalse(result.get().assignmentInEffect().hasPayroll());
    }

    @Test
    @DisplayName("findById returns hasPayroll = true when a payroll summary exists")
    void findByIdReturnsHasPayrollTrue() {
        Employee existing = employee();
        EmployeeWithInEffectAssignmentDto baseDto = dtoWithAssignment(assignment);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of(assignment));
        when(mapper.toDto(existing, assignment)).thenReturn(baseDto);
        when(payrollSummaryRepository.hasAnySummaryForAssignment(ASSIGNMENT_1))
                .thenReturn(true);

        Optional<EmployeeWithInEffectAssignmentDto> result = service.findById(EMPLOYEE_ID);

        assertTrue(result.isPresent());
        assertTrue(result.get().assignmentInEffect().hasPayroll());
    }

    @Test
    @DisplayName("findById returns no assignment when there is no in-effect")
    void findByIdWithoutInEffectAssignment() {
        Employee existing = employee();
        EmployeeWithInEffectAssignmentDto noAssignment = dtoWithoutAssignment();

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));
        when(assignmentRepository.list(any(EmployeePeriodicAssignmentCriteria.class)))
                .thenReturn(List.of());
        when(mapper.toDto(existing, null)).thenReturn(noAssignment);

        Optional<EmployeeWithInEffectAssignmentDto> result = service.findById(EMPLOYEE_ID);

        assertTrue(result.isPresent());
        assertNull(result.get().assignmentInEffect());
        verify(payrollSummaryRepository, never()).hasAnySummaryForAssignment(any());
    }

    @Test
    @DisplayName("findById returns empty when the employee does not exist")
    void findByIdMissingEmployee() {
        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());

        assertTrue(service.findById(EMPLOYEE_ID).isEmpty());
        verify(payrollSummaryRepository, never()).hasAnySummaryForAssignment(any());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private EmployeeCriteria criteria() {
        return EmployeeCriteria.builder()
                .page(0)
                .size(20)
                .sortBy("matricule")
                .sortDirection(SortDirection.ASC)
                .build();
    }

    private Employee employee() {
        return Employee.builder()
                .id(EMPLOYEE_ID)
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
    }

    private Country country(UUID id) {
        return Country.builder()
                .id(id).isoCode("XX").name("Placeholder").currencyCode("XXX").build();
    }

    private Department department(UUID id) {
        return Department.builder()
                .id(id).code("ENG").name("Engineering").build();
    }

    private Role role(UUID id) {
        return Role.builder()
                .id(id).code("SWE").name("Engineer").build();
    }

    private EmployeePeriodicAssignment assignment(UUID id, boolean inEffect) {
        return EmployeePeriodicAssignment.builder()
                .id(id)
                .employeeId(EMPLOYEE_ID)
                .startDate(LocalDate.of(2025, 10, 1))
                .endDate(inEffect ? null : LocalDate.of(2026, 6, 30))
                .country(country(FR))
                .department(department(ENGINEERING))
                .role(role(ENGINEER))
                .employmentType(EmploymentType.FULL_TIME)
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY)
                .inEffect(inEffect)
                .build();
    }

    private EmployeeSaveRequest saveRequest(
            String matricule, String name, String email, UUID originCountryId,
            EmployeeAssignmentSaveRequest assignment, EmployeeAssignmentToCloseRequest toClose) {
        return EmployeeSaveRequest.builder()
                .matricule(matricule)
                .name(name)
                .email(email)
                .originCountryId(originCountryId)
                .assignmentInEffect(assignment)
                .toClose(toClose)
                .build();
    }

    private EmployeeAssignmentSaveRequest saveAssignment(
            UUID id, UUID countryId, UUID departmentId, UUID roleId,
            EmploymentType employmentType, PayrollPeriodFrequency frequency,
            LocalDate startDate, LocalDate endDate) {
        return EmployeeAssignmentSaveRequest.builder()
                .id(id)
                .countryId(countryId)
                .departmentId(departmentId)
                .roleId(roleId)
                .employmentType(employmentType)
                .payrollFrequency(frequency)
                .startDate(startDate)
                .endDate(endDate)
                .build();
    }

    private EmployeeWithInEffectAssignmentDto dtoWithAssignment(EmployeePeriodicAssignment a) {
        return EmployeeWithInEffectAssignmentDto.builder()
                .id(EMPLOYEE_ID)
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .assignmentInEffect(EmployeeAssignmentDto.builder()
                        .id(a.getId())
                        .startDate(a.getStartDate())
                        .endDate(a.getEndDate())
                        .country(new RefDto(FR, "FR", "France"))
                        .department(new RefDto(ENGINEERING, "ENG", "Engineering"))
                        .role(new RefDto(ENGINEER, "SWE", "Engineer"))
                        .employmentType(a.getEmploymentType())
                        .payrollFrequency(a.getPayrollFrequency())
                        .hasPayroll(null)
                        .build())
                .build();
    }

    private EmployeeWithInEffectAssignmentDto dtoWithoutAssignment() {
        return EmployeeWithInEffectAssignmentDto.builder()
                .id(EMPLOYEE_ID)
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
    }
}