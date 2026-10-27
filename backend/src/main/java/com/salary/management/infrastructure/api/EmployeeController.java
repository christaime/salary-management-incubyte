package com.salary.management.infrastructure.api;


import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.application.service.EmployeeService;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee data with the assignment currently in effect")
public class EmployeeController {

    private final EmployeeService service;

    @GetMapping
    @Operation(summary = "Search and paginate employees with their in-effect assignment")
    public ResponseEntity<DataPage<EmployeeWithInEffectAssignmentDto>> search(
            @Parameter(description = "Keyword search in matricule, name, or email")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Exact matricule match")
            @RequestParam(required = false) String matricule,

            @Parameter(description = "Filter by origin country ids")
            @RequestParam(required = false) Set<UUID> originCountryIds,

            @Parameter(description = "Filter by assignment country ids")
            @RequestParam(required = false) Set<UUID> assignmentCountryIds,

            @Parameter(description = "Filter by department ids")
            @RequestParam(required = false) Set<UUID> departmentIds,

            @Parameter(description = "Filter by role ids")
            @RequestParam(required = false) Set<UUID> roleIds,

            @Parameter(description = "Filter by employment types")
            @RequestParam(required = false) Set<EmploymentType> employmentTypes,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field (matricule, name, email, birthDate)")
            @RequestParam(defaultValue = "matricule") String sortBy,

            @Parameter(description = "Sort direction (ASC or DESC)")
            @RequestParam(defaultValue = "ASC") SortDirection sortDirection
    ) {
        EmployeeCriteria criteria = EmployeeCriteria.builder()
                .keyword(keyword)
                .matricule(matricule)
                .anyOriginCountryIds(originCountryIds)
                .anyAssignmentCountryIds(assignmentCountryIds)
                .anyDepartmentIds(departmentIds)
                .anyRoleIds(roleIds)
                .anyEmploymentTypes(employmentTypes)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        return ResponseEntity.ok(service.page(criteria));
    }
}