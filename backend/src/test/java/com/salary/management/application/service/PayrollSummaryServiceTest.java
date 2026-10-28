package com.salary.management.application.service;

import com.salary.management.application.dto.payroll.*;
import com.salary.management.application.mapper.PayrollSummaryApiMapper;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.*;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amounts;
import com.salary.management.domain.repository.*;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollSummaryServiceTest {

    @Mock private PayrollSummaryRepository summaryRepository;
    @Mock private PayrollPeriodRepository periodRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private EmployeePeriodicAssignmentRepository assignmentRepository;
    @Mock private CountryRepository countryRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PayrollSummaryApiMapper mapper;
    @Mock private PayrollSummaryValidator validator;

    @InjectMocks private PayrollSummaryService service;

    private UUID summaryId, employeeId, periodId, assignmentId, countryId, deptId, roleId;
    private Employee employee;
    private PayrollPeriod period;
    private EmployeePeriodicAssignment assignment;
    private Country country;
    private Department department;
    private Role role;
    private PayrollSummary summary;

    @BeforeEach
    void setup() {
        summaryId = UUID.randomUUID();
        employeeId = UUID.randomUUID();
        periodId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();
        countryId = UUID.randomUUID();
        deptId = UUID.randomUUID();
        roleId = UUID.randomUUID();

        employee = Employee.builder()
                .id(employeeId).matricule("EMP-0001")
                .name("Ada").email("ada@example.com").build();
        period = PayrollPeriod.builder()
                .id(periodId).name("Jan 2026")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false).build();
        country = Country.builder().id(countryId)
                .isoCode("FR").name("France").currencyCode("EUR").build();
        department = Department.builder().id(deptId).code("ENG").name("Engineering").build();
        role = Role.builder().id(roleId).code("SWE").name("Engineer").build();
        assignment = EmployeePeriodicAssignment.builder()
                .id(assignmentId).employeeId(employeeId)
                .startDate(LocalDate.of(2025, 10, 1)).endDate(null)
                .country(country).department(department).role(role)
                .employmentType(EmploymentType.FULL_TIME)
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY)
                .inEffect(true).build();
        summary = PayrollSummary.builder()
                .id(summaryId).employeeId(employeeId)
                .payrollPeriod(period)
                .employeePeriodicAssignmentId(assignmentId)
                .amounts(Amounts.builder()
                        .base(new BigDecimal("1000.00"))
                        .variable(new BigDecimal("100.00"))
                        .allowances(new BigDecimal("50.00"))
                        .employerTaxes(new BigDecimal("200.00"))
                        .currency("EUR").build())
                .status(PayrollSummaryStatus.DRAFT).build();
    }

    // -------- create --------

    @Test
    @DisplayName("create persists and returns a DRAFT summary")
    void createPersistsDraft() {
        PayrollSummaryCreateRequest request = PayrollSummaryCreateRequest.builder()
                .employeeId(employeeId).periodId(periodId).assignmentId(assignmentId)
                .base(new BigDecimal("1000.00"))
                .variable(new BigDecimal("100.00"))
                .allowances(new BigDecimal("50.00"))
                .employerTaxes(new BigDecimal("200.00"))
                .currency("EUR").build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(summaryRepository.findByEmployeeIdAndPayrollPeriodId(employeeId, periodId))
                .thenReturn(Optional.empty());
        when(summaryRepository.save(any(PayrollSummary.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toDetail(any(), eq(employee), eq(country), eq(department), eq(role), eq(assignment)))
                .thenReturn(mock(PayrollSummaryDetailDto.class));

        service.create(request);

        verify(summaryRepository).save(any(PayrollSummary.class));
    }

    @Test
    @DisplayName("create rejects when the assignment does not belong to the employee")
    void createRejectsAssignmentNotForEmployee() {
        PayrollSummaryCreateRequest request = PayrollSummaryCreateRequest.builder()
                .employeeId(employeeId).periodId(periodId).assignmentId(assignmentId)
                .base(BigDecimal.ONE).variable(BigDecimal.ONE)
                .allowances(BigDecimal.ONE).employerTaxes(BigDecimal.ONE)
                .currency("EUR").build();

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
        EmployeePeriodicAssignment foreign = EmployeePeriodicAssignment.builder()
                .id(assignmentId).employeeId(UUID.randomUUID())
                .startDate(LocalDate.of(2025, 10, 1))
                .country(country).department(department).role(role)
                .employmentType(EmploymentType.FULL_TIME)
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY).inEffect(true).build();
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(foreign));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.create(request));
        assertEquals("ASSIGNMENT_NOT_FOR_EMPLOYEE", ex.getCode());
    }

    @Test
    @DisplayName("create rejects when the period is closed")
    void createRejectsClosedPeriod() {
        PayrollPeriod closed = PayrollPeriod.builder()
                .id(periodId).name("Jan 2026")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY).closed(true).build();
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(closed));

        PayrollSummaryCreateRequest request = PayrollSummaryCreateRequest.builder()
                .employeeId(employeeId).periodId(periodId).assignmentId(assignmentId)
                .base(BigDecimal.ONE).variable(BigDecimal.ONE)
                .allowances(BigDecimal.ONE).employerTaxes(BigDecimal.ONE)
                .currency("EUR").build();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.create(request));
        assertEquals("PERIOD_CLOSED", ex.getCode());
    }

    @Test
    @DisplayName("create rejects when the assignment does not cover the period")
    void createRejectsAssignmentNotCoveringPeriod() {
        EmployeePeriodicAssignment shortAssignment = EmployeePeriodicAssignment.builder()
                .id(assignmentId).employeeId(employeeId)
                .startDate(LocalDate.of(2026, 1, 15))
                .country(country).department(department).role(role)
                .employmentType(EmploymentType.FULL_TIME)
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY).inEffect(true).build();
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(shortAssignment));

        PayrollSummaryCreateRequest request = PayrollSummaryCreateRequest.builder()
                .employeeId(employeeId).periodId(periodId).assignmentId(assignmentId)
                .base(BigDecimal.ONE).variable(BigDecimal.ONE)
                .allowances(BigDecimal.ONE).employerTaxes(BigDecimal.ONE)
                .currency("EUR").build();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.create(request));
        assertEquals("ASSIGNMENT_DOES_NOT_COVER_PERIOD", ex.getCode());
    }

    @Test
    @DisplayName("create rejects when a summary already exists for (employee, period)")
    void createRejectsDuplicateSummary() {
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(summaryRepository.findByEmployeeIdAndPayrollPeriodId(employeeId, periodId))
                .thenReturn(Optional.of(summary));

        PayrollSummaryCreateRequest request = PayrollSummaryCreateRequest.builder()
                .employeeId(employeeId).periodId(periodId).assignmentId(assignmentId)
                .base(BigDecimal.ONE).variable(BigDecimal.ONE)
                .allowances(BigDecimal.ONE).employerTaxes(BigDecimal.ONE)
                .currency("EUR").build();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.create(request));
        assertEquals("SUMMARY_ALREADY_EXISTS", ex.getCode());
    }

    // -------- update --------

    @Test
    @DisplayName("update rejects when summary is not DRAFT")
    void updateRejectsNonDraft() {
        PayrollSummary validated = summary.withStatus(PayrollSummaryStatus.VALIDATED);
        when(summaryRepository.findById(summaryId)).thenReturn(Optional.of(validated));

        PayrollSummaryUpdateRequest request = PayrollSummaryUpdateRequest.builder()
                .assignmentId(assignmentId)
                .base(BigDecimal.ONE).variable(BigDecimal.ONE)
                .allowances(BigDecimal.ONE).employerTaxes(BigDecimal.ONE)
                .currency("EUR").build();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.update(summaryId, request));
        assertEquals("SUMMARY_NOT_DRAFT", ex.getCode());
    }

    @Test
    @DisplayName("update rejects when period is closed")
    void updateRejectsClosedPeriod() {
        PayrollSummary draft = summary;
        PayrollPeriod closed = PayrollPeriod.builder()
                .id(periodId).name("Jan 2026")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY).closed(true).build();
        when(summaryRepository.findById(summaryId)).thenReturn(Optional.of(draft));
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(closed));

        PayrollSummaryUpdateRequest request = PayrollSummaryUpdateRequest.builder()
                .assignmentId(assignmentId)
                .base(BigDecimal.ONE).variable(BigDecimal.ONE)
                .allowances(BigDecimal.ONE).employerTaxes(BigDecimal.ONE)
                .currency("EUR").build();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.update(summaryId, request));
        assertEquals("PERIOD_CLOSED", ex.getCode());
    }

    @Test
    @DisplayName("update persists the new amounts on DRAFT")
    void updatePersistsAmounts() {
        when(summaryRepository.findById(summaryId)).thenReturn(Optional.of(summary));
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));
        when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
        when(summaryRepository.save(any(PayrollSummary.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // toDetail(...) resolves these:
        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        when(countryRepository.findById(countryId)).thenReturn(Optional.of(country));
        when(departmentRepository.findById(deptId)).thenReturn(Optional.of(department));
        when(roleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(mapper.toDetail(any(), any(), any(), any(), any(), any()))
                .thenReturn(mock(PayrollSummaryDetailDto.class));

        PayrollSummaryUpdateRequest request = PayrollSummaryUpdateRequest.builder()
                .assignmentId(assignmentId)
                .base(new BigDecimal("2000.00"))
                .variable(new BigDecimal("200.00"))
                .allowances(new BigDecimal("100.00"))
                .employerTaxes(new BigDecimal("400.00"))
                .currency("EUR").build();

        service.update(summaryId, request);

        ArgumentCaptor<PayrollSummary> captor =
                ArgumentCaptor.forClass(PayrollSummary.class);
        verify(summaryRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getAmounts().getBase()
                .compareTo(new BigDecimal("2000.00")));
    }

    // -------- delete --------

    @Test
    @DisplayName("delete rejects non-DRAFT")
    void deleteRejectsNonDraft() {
        when(summaryRepository.findById(summaryId))
                .thenReturn(Optional.of(summary.withStatus(PayrollSummaryStatus.VALIDATED)));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.delete(summaryId));
        assertEquals("SUMMARY_NOT_DRAFT", ex.getCode());
    }

    @Test
    @DisplayName("delete removes a DRAFT summary")
    void deleteRemovesDraft() {
        when(summaryRepository.findById(summaryId)).thenReturn(Optional.of(summary));

        service.delete(summaryId);

        verify(summaryRepository).deleteById(summaryId);
    }

    // -------- validate --------

    @Test
    @DisplayName("validate delegates to the validator")
    void validateDelegates() {
        PayrollSummaryDetailDto expected = mock(PayrollSummaryDetailDto.class);
        when(validator.validate(summaryId)).thenReturn(expected);

        PayrollSummaryDetailDto result = service.validate(summaryId);

        assertSame(expected, result);
    }

    // -------- validateAll --------

    @Test
    @DisplayName("validateAll loops through every DRAFT and reports failures")
    void validateAllLoopsAndReportsFailures() {
        UUID failId = UUID.randomUUID();
        PayrollSummary pass = summary;
        PayrollSummary fail = PayrollSummary.builder()
                .id(failId).employeeId(employeeId).payrollPeriod(period)
                .employeePeriodicAssignmentId(assignmentId)
                .amounts(summary.getAmounts())
                .status(PayrollSummaryStatus.DRAFT).build();

        // The service reads the period up-front; stub it.
        when(periodRepository.findById(periodId)).thenReturn(Optional.of(period));

        // First page: both. Second page: empty.
        when(summaryRepository.page(any(PayrollSummaryCriteria.class)))
                .thenReturn(new DataPage<>(List.of(pass, fail), 0, 100, 2))
                .thenReturn(new DataPage<>(List.of(), 0, 100, 0));

        when(validator.validate(summaryId))
                .thenReturn(mock(PayrollSummaryDetailDto.class));
        when(validator.validate(failId))
                .thenThrow(new ConflictException("ASSIGNMENT_NOT_IN_EFFECT",
                        "Assignment not in effect"));

        ValidateAllResult result = service.validateAll(
                PayrollSummaryValidateRequest.builder().periodId(periodId).build());

        assertEquals(2, result.total());
        assertEquals(1, result.validated());
        assertEquals(1, result.failed());
        assertEquals(1, result.failures().size());
        assertEquals(failId, result.failures().get(0).summaryId());
        assertEquals("ASSIGNMENT_NOT_IN_EFFECT", result.failures().get(0).code());
    }

    @Test
    @DisplayName("validateAll rejects a missing period")
    void validateAllMissingPeriod() {
        when(periodRepository.findById(periodId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.validateAll(
                        PayrollSummaryValidateRequest.builder()
                                .periodId(periodId).build()));
    }
}