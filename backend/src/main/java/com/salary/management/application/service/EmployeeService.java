package com.salary.management.application.service;

import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.application.mapper.EmployeeApiMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.repository.EmployeePeriodicAssignmentRepository;
import com.salary.management.domain.repository.EmployeeRepository;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Reads employee data for the paginated employee listing. */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeePeriodicAssignmentRepository assignmentRepository;
    private final EmployeeApiMapper mapper;

    /**
     * Returns one page of employees, each carrying the assignment flagged
     * {@code in_effect = true} (or {@code null} when none is flagged).
     *
     * @param criteria pagination, sort, and filter criteria
     * @return the page of employees, mapped to the report DTO
     */
    @Transactional(readOnly = true)
    public DataPage<EmployeeWithInEffectAssignmentDto> page(EmployeeCriteria criteria) {
        log.debug("Employee page requested: page={}, size={}, sortBy={} {}, keyword={}, "
                        + "matricule={}",
                criteria.page(), criteria.size(),
                criteria.sortBy(), criteria.sortDirection(),
                criteria.keyword(), criteria.matricule());

        DataPage<Employee> employees = employeeRepository.page(criteria);

        List<EmployeeWithInEffectAssignmentDto> content = employees.content().stream()
                .map(this::toDtoWithAssignment)
                .toList();

        log.debug("Employee page returned: {} row(s) of {} total (page {} of {})",
                content.size(), employees.totalElements(), employees.totalElements(),
                employees.totalPages());

        return new DataPage<>(
                content,
                employees.page(),
                employees.size(),
                employees.totalElements());
    }

    private EmployeeWithInEffectAssignmentDto toDtoWithAssignment(Employee employee) {
        Optional<EmployeePeriodicAssignment> inEffect = assignmentRepository
                .list(EmployeePeriodicAssignmentCriteria.builder()
                        .employeeId(employee.getId())
                        .inEffect(true)
                        .build())
                .stream()
                .findFirst();

        if (inEffect.isEmpty()) {
            log.debug("No in-effect assignment for employee {}", employee.getId());
        }

        return mapper.toDto(employee, inEffect.orElse(null));
    }
}