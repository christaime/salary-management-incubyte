package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.aggregate.AggregateReportRequest;
import com.salary.management.application.dto.aggregate.AggregateReportResponse;
import com.salary.management.application.dto.ranking.EarningRankingReportRequest;
import com.salary.management.application.dto.ranking.EarningRankingReportResponse;
import com.salary.management.application.service.AggregateReportService;
import com.salary.management.application.service.RankingReportService;
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
public class ReportController {

    private final AggregateReportService aggregateReportService;
    private final RankingReportService rankingReportService;

    @PostMapping("/aggregate")
    @Operation(summary = "Generate an aggregate payroll report")
    public ResponseEntity<AggregateReportResponse> generate(
            @Valid @RequestBody AggregateReportRequest request) {
        return ResponseEntity.ok(aggregateReportService.generate(request));
    }

    @PostMapping("/rankings")
    @Operation(summary = "Generate a ranking payroll report")
    public ResponseEntity<EarningRankingReportResponse> rankings(
            @Valid @RequestBody EarningRankingReportRequest request) {
        return ResponseEntity.ok(rankingReportService.generate(request));
    }
}