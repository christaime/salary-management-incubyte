package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.employee.EmployeeWithInEffectAssignmentDto;
import com.salary.management.application.dto.period.*;
import com.salary.management.application.service.PayrollPeriodService;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payroll-periods")
@RequiredArgsConstructor
@Tag(name = "Payroll Periods", description = "Manage payroll periods")
public class PayrollPeriodController {

    private final PayrollPeriodService service;

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Search and paginate payroll periods")
    public ResponseEntity<DataPage<PayrollPeriodResponse>> search(
            @Parameter(description = "Keyword search in name")
            @RequestParam(required = false) @Size(max = 500) String keyword,

            @Parameter(description = "Inclusive lower bound on start date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,

            @Parameter(description = "Inclusive upper bound on end date (YYYY-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,

            @Parameter(description = "Frequency filter")
            @RequestParam(required = false) PayrollPeriodFrequency frequency,

            @Parameter(description = "Closed status filter")
            @RequestParam(required = false) Boolean isClosed,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Max(100) int size,

            @Parameter(description = "Sort field (startDate, endDate, name)")
            @RequestParam(defaultValue = "startDate") @Size(max = 100) String sortBy,

            @Parameter(description = "Sort direction (ASC or DESC)")
            @RequestParam(defaultValue = "DESC") SortDirection sortDirection
    ) {
        PayrollPeriodCriteria criteria = PayrollPeriodCriteria.builder()
                .keyword(keyword)
                .startDate(startDate)
                .endDate(endDate)
                .frequency(frequency)
                .isClosed(isClosed)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        return ResponseEntity.ok(service.search(criteria));
    }

    // ------------------------------------------------------------------
    // Read — single
    // ------------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<PayrollPeriodResponse> findById(@PathVariable UUID id) {
        return service.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payroll period not found: " + id));
    }
    // ------------------------------------------------------------------
    // Create
    // ------------------------------------------------------------------

    @PostMapping
    @Operation(
            summary = "Create a single payroll period",
            description = "Calendar validity is enforced per frequency (monthly = full calendar "
                    + "month, bi-weekly = exactly 14 days). Name and date range must be unique "
                    + "per frequency; overlapping ranges of the same frequency are rejected.")
    @ApiResponse(responseCode = "201", description = "Period created")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "409", description = "Name or date range conflicts with an existing period")
    public ResponseEntity<PayrollPeriodResponse> create(
            @Valid @RequestBody PayrollPeriodCreateRequest request) {
        PayrollPeriodResponse created = service.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/batch")
    @Operation(
            summary = "Create a batch of payroll periods",
            description = "Monthly periods are saved first, then bi-weekly ones, each sorted "
                    + "ascending by start date, each in its own transaction. A failing item does "
                    + "not roll back its siblings. Returns 201 when every item succeeds and 200 "
                    + "when some items fail; the body lists per-item outcomes.")
    @ApiResponse(responseCode = "201", description = "All periods created")
    @ApiResponse(responseCode = "200", description = "Some periods created, some rejected")
    @ApiResponse(responseCode = "400", description = "The request body is not a list of valid periods")
    public ResponseEntity<PayrollPeriodBatchCreationResult> createBatch(
            @Valid @RequestBody
            @NotEmpty(message = "At least one payroll period must be provided")
            List<PayrollPeriodCreateRequest> requests) {
        PayrollPeriodBatchCreationResult result = service.createBatch(requests);
        HttpStatus status = result.failed().isEmpty()
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/generate-sub")
    @Operation(
            summary = "Preview the bi-weekly sub-periods for a monthly range",
            description = "Returns the bi-weekly sub-periods that would be generated "
                    + "for a monthly schedule spanning [startDate, endDate), split into "
                    + "ones that don't yet exist (`new`) and ones already on the chain (`exist`). "
                    + "Nothing is created.")
    public ResponseEntity<SubPayrollPeriodCreateResponse> generateSub(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        return ResponseEntity.ok(service.previewSubPeriods(startDate, endDate));
    }

    // ------------------------------------------------------------------
    // Update
    // ------------------------------------------------------------------

    @PutMapping("/{id}")
    @Operation(
            summary = "Update a payroll period",
            description = "A settled period cannot be edited. A period referenced by payroll "
                    + "records can only have its name changed; dates and frequency are frozen.")
    @ApiResponse(responseCode = "200", description = "Period updated")
    @ApiResponse(responseCode = "400", description = "Validation failed")
    @ApiResponse(responseCode = "404", description = "Period not found")
    @ApiResponse(responseCode = "409",
            description = "Period is settled, or referenced with a non-name change, or name conflicts")
    public ResponseEntity<PayrollPeriodResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody PayrollPeriodCreateRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    // ------------------------------------------------------------------
    // Delete
    // ------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a payroll period",
            description = "Only unsettled periods with no payroll records can be deleted.")
    @ApiResponse(responseCode = "204", description = "Period deleted")
    @ApiResponse(responseCode = "404", description = "Period not found")
    @ApiResponse(responseCode = "409", description = "Period is settled or referenced")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/batch")
    @Operation(
            summary = "Delete several payroll periods in one transaction",
            description = "All-or-nothing: if any item is settled or referenced, nothing is "
                    + "deleted and the response names the offending id. The caller sends a "
                    + "monthly period together with its bi-weekly children when deleting a block.")
    @ApiResponse(responseCode = "204", description = "All periods deleted")
    @ApiResponse(responseCode = "400", description = "The request body is empty")
    @ApiResponse(responseCode = "404", description = "One of the ids does not exist")
    @ApiResponse(responseCode = "409", description = "One of the periods is settled or referenced")
    public ResponseEntity<Void> deleteBatch(
            @Valid @RequestBody
            @NotEmpty(message = "At least one payroll period id must be provided")
            List<UUID> ids) {
        service.deleteBatch(ids);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------
    // Close
    // ------------------------------------------------------------------

    @PostMapping("/{id}/check-close")
    @Operation(
            summary = "Pre-flight a monthly pay schedule close",
            description = "Runs the coverage checks without writing anything. Returns the "
                    + "periods that would be closed and the warnings to accept, or 409 with "
                    + "the blockers that prevent the close. Monthly periods only.")
    @ApiResponse(responseCode = "200", description = "Pre-flight succeeded; warnings are in the body")
    @ApiResponse(responseCode = "400", description = "The id does not refer to a monthly period")
    @ApiResponse(responseCode = "404", description = "Period not found")
    @ApiResponse(responseCode = "409", description = "The period is settled, or blockers prevent the close")
    public ResponseEntity<ClosePeriodResponse> checkClose(@PathVariable UUID id) {
        return ResponseEntity.ok(service.checkClose(id));
    }

    @PostMapping("/{id}/close")
    @Operation(
            summary = "Settle a monthly pay schedule and its bi-weekly sub-periods",
            description = "Builds the snapshot as DRAFT, promotes it to VALIDATED, and marks "
                    + "the monthly period and every bi-weekly whose start date falls inside "
                    + "the monthly range as closed. One transaction. Returns 409 with blockers "
                    + "when the close cannot proceed. Monthly periods only.")
    @ApiResponse(responseCode = "200", description = "Period and sub-periods closed; warnings are in the body")
    @ApiResponse(responseCode = "400", description = "The id does not refer to a monthly period")
    @ApiResponse(responseCode = "404", description = "Period not found")
    @ApiResponse(responseCode = "409", description = "The period is settled, or blockers prevent the close")
    public ResponseEntity<ClosePeriodResponse> close(@PathVariable UUID id) {
        return ResponseEntity.ok(service.close(id));
    }
}