package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.AggregateLineDto;
import com.salary.management.application.dto.AggregateReportRequest;
import com.salary.management.application.dto.AggregateReportResponse;
import com.salary.management.application.dto.AmountDto;
import com.salary.management.application.dto.DateRange;
import com.salary.management.application.service.AggregateReportService;
import com.salary.management.domain.model.enums.GroupKey;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer tests for {@link AggregateReportController}. */
@WebMvcTest(controllers = AggregateReportController.class)
class AggregateReportControllerTest {

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
}