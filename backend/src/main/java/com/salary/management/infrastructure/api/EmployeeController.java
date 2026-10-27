package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.employee.EmployeeSaveRequest;
import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.application.service.EmployeeService;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
@Tag(name = "Employees", description = "Employee data with the assignment currently in effect")
public class EmployeeController {

    private final EmployeeService service;

    // ------------------------------------------------------------------
    // Read — paginated search
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Search and paginate employees with their in-effect assignment")
    public ResponseEntity<DataPage<EmployeeWithInEffectAssignmentDto>> search(
            @Parameter(description = "Keyword search in matricule, name, or email")
            @RequestParam(required = false) @Size(max = 500) String keyword,

            @Parameter(description = "Exact matricule match")
            @RequestParam(required = false) @Size(max = 100) String matricule,

            @Parameter(description = "Filter by origin country ids")
            @RequestParam(name = "anyOriginCountryIds", required = false) @Size(max=50) Set<UUID> originCountryIds,

            @Parameter(description = "Filter by assignment country ids")
            @RequestParam(name = "anyAssignmentCountryIds", required = false) @Size(max=50) Set<UUID> assignmentCountryIds,

            @Parameter(description = "Filter by department ids")
            @RequestParam(name = "anyDepartmentIds", required = false) @Size(max=50) Set<UUID> departmentIds,

            @Parameter(description = "Filter by role ids")
            @RequestParam(name = "anyRoleIds", required = false) @Size(max=50) Set<UUID> roleIds,

            @Parameter(description = "Filter by employment types")
            @RequestParam(required = false) @Size(max = 50) Set<EmploymentType> employmentTypes,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Max(100) int size,

            @Parameter(description = "Sort field (matricule, name, email, birthDate)")
            @RequestParam(defaultValue = "matricule") @Size(max = 100) String sortBy,

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

        log.info("Employee search requested: keyword={}, matricule={}, "
                        + "originCountryIds={}, assignmentCountryIds={}, "
                        + "departmentIds={}, roleIds={}, employmentTypes={}, "
                        + "page={}, size={}, sortBy={} {}",
                criteria.keyword(), criteria.matricule(),
                criteria.anyOriginCountryIds(), criteria.anyAssignmentCountryIds(),
                criteria.anyDepartmentIds(), criteria.anyRoleIds(),
                criteria.anyEmploymentTypes(),
                criteria.page(), criteria.size(),
                criteria.sortBy(), criteria.sortDirection());

        DataPage<EmployeeWithInEffectAssignmentDto> response = service.page(criteria);

        log.info("Employee search returned: {} row(s) of {} total (page {} of {})",
                response.content().size(), response.totalElements(),
                response.page(), response.totalPages());

        return ResponseEntity.ok(response);
    }

    // ------------------------------------------------------------------
    // Read — single
    // ------------------------------------------------------------------

    @GetMapping("/{id}")
    @Operation(summary = "Get an employee and the assignment currently in effect")
    public ResponseEntity<EmployeeWithInEffectAssignmentDto> findById(@PathVariable UUID id) {
        log.info("Employee lookup requested: id={}", id);
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @PostMapping
    @Operation(summary = "Create an employee with their initial in-effect assignment")
    public ResponseEntity<EmployeeWithInEffectAssignmentDto> create(
            @Valid @RequestBody EmployeeSaveRequest request) {
        log.info("Employee create requested: matricule={}, email={}",
                request.matricule(), request.email());
        EmployeeWithInEffectAssignmentDto created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/employees/" + created.id()))
                .body(created);
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @PutMapping("/{id}")
    @Operation(summary = "Update an employee and optionally change their in-effect assignment")
    public ResponseEntity<EmployeeWithInEffectAssignmentDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeSaveRequest request) {
        log.info("Employee update requested: id={}, hasAssignmentPayload={}, hasClosePayload={}",
                id, request.assignmentInEffect() != null, request.toClose() != null);
        return ResponseEntity.ok(service.update(id, request));
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an employee and all their assignments")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("Employee delete requested: id={}", id);
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}