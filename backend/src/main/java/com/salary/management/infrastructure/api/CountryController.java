package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.CountryResponse;
import com.salary.management.application.service.CountryService;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.pagination.SortDirection;
import com.salary.management.domain.repository.criteria.CountryCriteria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/countries")
@RequiredArgsConstructor
@Tag(name = "Countries", description = "Reference data for countries")
public class CountryController {

    private final CountryService service;

    @GetMapping
    @Operation(summary = "Search and paginate countries")
    public ResponseEntity<DataPage<CountryResponse>> search(
            @Parameter(description = "Keyword search in name or ISO code")
            @RequestParam(required = false) @Size(max = 500) String keyword,

            @Parameter(description = "Code of the Currency used in the country (USD, EUR, ...)")
            @RequestParam(required = false) @Size(max = 3) String currencyCode,

            @Parameter(description = "Page number (0-indexed)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") @Max(100) int size,

            @Parameter(description = "Sort field (name, isoCode, currencyCode)")
            @RequestParam(defaultValue = "name") @Size(max = 100) String sortBy,

            @Parameter(description = "Sort direction (ASC or DESC)")
            @RequestParam(defaultValue = "ASC") SortDirection sortDirection
    ) {
        CountryCriteria criteria = CountryCriteria.builder()
                .keyword(keyword)
                .currencyCode(currencyCode)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDirection(sortDirection)
                .build();

        return ResponseEntity.ok(service.search(criteria));
    }
}