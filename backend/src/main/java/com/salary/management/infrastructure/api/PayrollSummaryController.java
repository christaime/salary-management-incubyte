package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.payroll.*;
import com.salary.management.application.service.PayrollSummaryService;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/payroll-summaries")
@RequiredArgsConstructor
@Tag(name = "Payroll summaries", description = "Payroll summary recording and validation")
public class PayrollSummaryController {

    private final PayrollSummaryService service;

    @GetMapping
    @Operation(summary = "Search and paginate payroll summaries")
    public ResponseEntity<DataPage<PayrollSummaryDto>> search(
            @RequestParam LocalDate rangeStart,
            @RequestParam LocalDate rangeEnd,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) String employeeMatricule,
            @RequestParam(name = "anyCountryIds", required = false) Set<UUID> countryIds,
            @RequestParam(name = "anyDepartmentIds", required = false) Set<UUID> departmentIds,
            @RequestParam(name = "anyRoleIds", required = false) Set<UUID> roleIds,
            @RequestParam(required = false) Boolean nonExistent,
            @RequestParam(required = false) Set<EmploymentType> anyEmploymentTypes,
            @RequestParam(required = false) PayrollSummaryStatus status,
            @RequestParam(required = false) PayrollPeriodFrequency payrollFrequency,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "payrollPeriod.startDate") String sortBy,
            @RequestParam(defaultValue = "DESC") SortDirection sortDirection) {

        PayrollSummarySearchRequest request = PayrollSummarySearchRequest.builder()
                .rangeStart(rangeStart)
                .rangeEnd(rangeEnd)
                .employeeId(employeeId)
                .employeeMatricule(employeeMatricule)
                .anyCountryIds(countryIds)
                .anyDepartmentIds(departmentIds)
                .anyRoleIds(roleIds)
                .anyEmploymentTypes(anyEmploymentTypes)
                .status(status)
                .nonExistent(nonExistent)
                .payrollFrequency(payrollFrequency)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        return ResponseEntity.ok(service.page(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one payroll summary")
    public ResponseEntity<PayrollSummaryDetailDto> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a payroll summary")
    public ResponseEntity<PayrollSummaryDetailDto> create(
            @Valid @RequestBody PayrollSummaryCreateRequest request) {
        PayrollSummaryDetailDto created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/payroll-summaries/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a DRAFT payroll summary")
    public ResponseEntity<PayrollSummaryDetailDto> update(
            @PathVariable UUID id,
            @Valid @RequestBody PayrollSummaryUpdateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a DRAFT payroll summary")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Validate a payroll summary")
    public ResponseEntity<PayrollSummaryDetailDto> validate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.validate(id));
    }

    @PostMapping("/validate-all")
    @Operation(summary = "Validate every DRAFT payroll summary in a period, applying the filters")
    public ResponseEntity<ValidateAllResult> validateAll(
            @Valid @RequestBody PayrollSummaryValidateRequest request) {
        return ResponseEntity.ok(service.validateAll(request));
    }
}