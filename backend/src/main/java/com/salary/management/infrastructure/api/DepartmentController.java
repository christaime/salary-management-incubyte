package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.ReferenceDataResponse;
import com.salary.management.application.service.DepartmentService;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.repository.criteria.DepartmentCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
@Tag(name = "Departments", description = "Reference data for departments")
public class DepartmentController {

    private final DepartmentService service;

    @GetMapping
    @Operation(summary = "Search and paginate departments")
    public ResponseEntity<DataPage<ReferenceDataResponse>> search(
            @Parameter(description = "Keyword search in name or code")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Exact code match")
            @RequestParam(required = false) String code,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size,

            @Parameter(description = "Sort field (name, code)")
            @RequestParam(defaultValue = "name") String sortBy,

            @Parameter(description = "Sort direction (ASC or DESC)")
            @RequestParam(defaultValue = "ASC") SortDirection sortDirection
    ) {
        DepartmentCriteria criteria = DepartmentCriteria.builder()
                .keyword(keyword)
                .code(code)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        return ResponseEntity.ok(service.search(criteria));
    }
}