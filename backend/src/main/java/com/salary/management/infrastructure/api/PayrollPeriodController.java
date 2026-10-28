package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.PayrollPeriodResponse;
import com.salary.management.application.service.PayrollPeriodService;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/payroll-periods")
@RequiredArgsConstructor
@Tag(name = "Payroll Periods", description = "Reference data for payroll periods")
public class PayrollPeriodController {

    private final PayrollPeriodService service;

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
}