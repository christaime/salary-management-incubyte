package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.AggregateReportRequest;
import com.salary.management.application.dto.AggregateReportResponse;
import com.salary.management.application.service.AggregateReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Payroll reports")
public class AggregateReportController {

    private final AggregateReportService service;

    @PostMapping("/aggregate")
    @Operation(summary = "Generate an aggregate payroll report")
    public ResponseEntity<AggregateReportResponse> generate(
            @Valid @RequestBody AggregateReportRequest request) {
        return ResponseEntity.ok(service.generate(request));
    }
}