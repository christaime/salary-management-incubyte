package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.*;
import com.salary.management.application.dto.aggregate.AggregateLineDto;
import com.salary.management.application.dto.aggregate.AggregateReportRequest;
import com.salary.management.application.dto.aggregate.AggregateReportResponse;
import com.salary.management.application.dto.ranking.EarnerRankingDto;
import com.salary.management.application.dto.ranking.EarningRankingReportRequest;
import com.salary.management.application.dto.ranking.EarningRankingReportResponse;
import com.salary.management.application.service.AggregateReportService;
import com.salary.management.application.service.RankingReportService;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.ReportKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer tests for {@link ReportController}. */
@WebMvcTest(controllers = ReportController.class)
class ReportControllerTest {

    private static final String VALID_BODY = """
            {
              "dateRange": { "startDate": "2026-01-01", "endDate": "2026-01-31" },
              "groupKeys": ["COUNTRY"],
              "includeBreakdown": false,
              "includeStatistics": false
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AggregateReportService service;
    @MockBean
    private RankingReportService rankingReportService;

    @Test
    @DisplayName("valid request returns 200 with the report shape")
    void validRequestReturns200() throws Exception {
        when(service.generate(any(AggregateReportRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/v1/reports/aggregate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("AGGREGATE"))
                .andExpect(jsonPath("$.referenceCurrency").value("USD"))
                .andExpect(jsonPath("$.groupKeys[0]").value("COUNTRY"))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].amount.currency").value("USD"))
                .andExpect(jsonPath("$.lines[0].amount.amount").value(1350.00))
                .andExpect(jsonPath("$.lines[0].employeeCount").value(2));
    }

    @Test
    @DisplayName("empty groupKeys returns 200 with a single total line")
    void emptyGroupKeysReturns200WithSingleLine() throws Exception {
        when(service.generate(any(AggregateReportRequest.class)))
                .thenReturn(responseWithEmptyGroupKeys());

        String body = """
                {
                  "dateRange": { "startDate": "2026-01-01", "endDate": "2026-01-31" },
                  "groupKeys": [],
                  "includeBreakdown": false,
                  "includeStatistics": false
                }
                """;

        mockMvc.perform(post("/api/v1/reports/aggregate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupKeys.length()").value(0))
                .andExpect(jsonPath("$.lines.length()").value(1));
    }

    @Test
    @DisplayName("missing dateRange returns 400 without calling the service")
    void missingDateRangeReturns400() throws Exception {
        String body = """
                {
                  "groupKeys": ["COUNTRY"],
                  "includeBreakdown": false,
                  "includeStatistics": false
                }
                """;

        mockMvc.perform(post("/api/v1/reports/aggregate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("null startDate in dateRange returns 400")
    void nullStartDateReturns400() throws Exception {
        String body = """
                {
                  "dateRange": { "endDate": "2026-01-31" },
                  "groupKeys": ["COUNTRY"],
                  "includeBreakdown": false,
                  "includeStatistics": false
                }
                """;

        mockMvc.perform(post("/api/v1/reports/aggregate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("missing groupKeys returns 400")
    void missingGroupKeysReturns400() throws Exception {
        String body = """
                {
                  "dateRange": { "startDate": "2026-01-01", "endDate": "2026-01-31" },
                  "includeBreakdown": false,
                  "includeStatistics": false
                }
                """;

        mockMvc.perform(post("/api/v1/reports/aggregate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("unknown group key value returns 400")
    void unknownGroupKeyReturns400() throws Exception {
        String body = """
                {
                  "dateRange": { "startDate": "2026-01-01", "endDate": "2026-01-31" },
                  "groupKeys": ["NOPE"],
                  "includeBreakdown": false,
                  "includeStatistics": false
                }
                """;

        mockMvc.perform(post("/api/v1/reports/aggregate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // ------------------------------------------------------------------
    // Rankings endpoint
    // ------------------------------------------------------------------

    private static final String VALID_RANKING_BODY = """
            {
              "periodId": "55555555-5555-5555-5555-555555555555",
              "groupKey": "COUNTRY",
              "countryId": "11111111-1111-1111-1111-111111111111"
            }
            """;

    @Test
    @DisplayName("rankings: valid request returns 200 with the report shape")
    void rankingsValidRequestReturns200() throws Exception {
        when(rankingReportService.generate(any(EarningRankingReportRequest.class)))
                .thenReturn(rankingResponse());

        mockMvc.perform(post("/api/v1/reports/rankings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_RANKING_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("RANKING"))
                .andExpect(jsonPath("$.referenceCurrency").value("USD"))
                .andExpect(jsonPath("$.groupKey").value("COUNTRY"))
                .andExpect(jsonPath("$.tops.length()").value(1))
                .andExpect(jsonPath("$.tops[0].rank").value(1))
                .andExpect(jsonPath("$.tops[0].employee.name").value("Grace Hopper"))
                .andExpect(jsonPath("$.bottoms.length()").value(0));
    }

    @Test
    @DisplayName("rankings: null periodId returns 400 without calling the service")
    void rankingsNullPeriodIdReturns400() throws Exception {
        String body = """
                {
                  "groupKey": "COUNTRY",
                  "countryId": "11111111-1111-1111-1111-111111111111"
                }
                """;

        mockMvc.perform(post("/api/v1/reports/rankings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rankingReportService);
    }

    @Test
    @DisplayName("rankings: empty groupKey (global) is accepted")
    void rankingsGlobalGroupKeyAccepted() throws Exception {
        when(rankingReportService.generate(any(EarningRankingReportRequest.class)))
                .thenReturn(rankingResponseWithEmptyGroupKey());

        String body = """
                {
                  "periodId": "55555555-5555-5555-5555-555555555555"
                }
                """;

        mockMvc.perform(post("/api/v1/reports/rankings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupKey").doesNotExist());
    }

    @Test
    @DisplayName("rankings: unknown group key value returns 400")
    void rankingsUnknownGroupKeyReturns400() throws Exception {
        String body = """
                {
                  "periodId": "55555555-5555-5555-5555-555555555555",
                  "groupKey": "NOPE"
                }
                """;

        mockMvc.perform(post("/api/v1/reports/rankings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(rankingReportService);
    }

    private AggregateReportResponse response() {
        AggregateLineDto line = new AggregateLineDto(
                new AmountDto(new BigDecimal("1350.00"), "USD"),
                2,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                List.of(GroupKey.COUNTRY),
                "USD",
                List.of(line),
                List.of());
    }

    private AggregateReportResponse responseWithEmptyGroupKeys() {
        AggregateLineDto line = new AggregateLineDto(
                new AmountDto(new BigDecimal("1350.00"), "USD"),
                2,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        return new AggregateReportResponse(
                ReportKind.AGGREGATE,
                new DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)),
                List.of(),
                "USD",
                List.of(line),
                List.of());
    }

    private EarningRankingReportResponse rankingResponse() {
        EarnerRankingDto top = new EarnerRankingDto(
                new BigDecimal("2500.00"),
                PayrollPeriodFrequency.MONTHLY,
                9900,
                1,
                new EmployeeRefDto(
                        UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01"),
                        "EMP-0001",
                        "Grace Hopper"),
                new RefDto(UUID.randomUUID(), "ENG", "Engineering"),
                new RefDto(UUID.randomUUID(), "SWE", "Software Engineer"),
                new RefDto(UUID.randomUUID(), "FR", "France"),
                EmploymentType.FULL_TIME,
                periodResponse());
        return EarningRankingReportResponse.builder()
                .kind(ReportKind.RANKING)
                .period(periodResponse())
                .referenceCurrency("USD")
                .groupKey(GroupKey.COUNTRY)
                .tops(List.of(top))
                .bottoms(List.of())
                .positionContext(null)
                .totalReferenceAmount(new BigDecimal("2500.00"))
                .totalDimensionReferenceAmount(new BigDecimal("2500.00"))
                .totalMonthlyPeriod(1)
                .totalBiWeeklyPeriod(0)
                .build();
    }

    private EarningRankingReportResponse rankingResponseWithEmptyGroupKey() {
        return EarningRankingReportResponse.builder()
                .kind(ReportKind.RANKING)
                .period(periodResponse())
                .referenceCurrency("USD")
                .groupKey(null)
                .tops(List.of())
                .bottoms(List.of())
                .positionContext(null)
                .totalReferenceAmount(BigDecimal.ZERO)
                .totalDimensionReferenceAmount(BigDecimal.ZERO)
                .totalMonthlyPeriod(1)
                .totalBiWeeklyPeriod(0)
                .build();
    }

    private PayrollPeriodResponse periodResponse() {
        return PayrollPeriodResponse.builder()
                .id("55555555-5555-5555-5555-555555555555")
                .name("January 2026")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }
}