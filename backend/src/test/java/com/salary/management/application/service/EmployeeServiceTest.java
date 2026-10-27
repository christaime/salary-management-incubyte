package com.salary.management.application.service;

import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.application.mapper.EmployeeApiMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.EmployeePeriodicAssignmentRepository;
import com.salary.management.domain.repository.EmployeeRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeePeriodicAssignmentRepository assignmentRepository;

    @Mock
    private EmployeeApiMapper mapper;

    @InjectMocks
    private EmployeeService service;

    private Employee employee;
    private EmployeePeriodicAssignment assignment;
    private EmployeeWithInEffectAssignmentDto dto;

    @BeforeEach
    void setup() {
        employee = Employee.builder()
                .id(UUID.randomUUID())
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
        assignment = EmployeePeriodicAssignment.builder()
                .employeeId(employee.getId())
                .startDate(LocalDate.of(2025, 10, 1))
                .country(Country.builder()
                        .id(UUID.randomUUID()).isoCode("FR").name("France").currencyCode("EUR").build())
                .department(Department.builder()
                        .id(UUID.randomUUID()).code("ENG").name("Engineering").build())
                .role(Role.builder()
                        .id(UUID.randomUUID()).code("SWE").name("Engineer").build())
                .employmentType(EmploymentType.FULL_TIME)
                .payrollFrequency(PayrollPeriodFrequency.MONTHLY)
                .inEffect(true)
                .build();
        dto = EmployeeWithInEffectAssignmentDto.builder()
                .id(employee.getId())
                .matricule("EMP-0001")
                .name("Ada Lovelace")
                .email("ada@example.com")
                .build();
    }

    // ------------------------------------------------------------------
    // Page mapping
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

    private EmployeeCriteria criteria() {
        return EmployeeCriteria.builder()
                .page(0)
                .size(20)
                .sortBy("matricule")
                .sortDirection(SortDirection.ASC)
                .build();
    }
}