package com.salary.management.infrastructure.api;

import com.salary.management.application.dto.AmountsDto;
import com.salary.management.application.dto.payroll.*;
import com.salary.management.application.service.PayrollSummaryService;
import com.salary.management.common.exception.ConflictException;
import com.salary.management.common.exception.ResourceNotFoundException;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PayrollSummaryController.class)
class PayrollSummaryControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private PayrollSummaryService service;

    @Test
    @DisplayName("GET /{id} returns the detail")
    void findByIdReturnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        PayrollSummaryDetailDto dto = mockDetail(id);
        when(service.findById(id)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/payroll-summaries/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /{id} returns 404 for missing summary")
    void findByIdReturns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.findById(id))
                .thenThrow(new ResourceNotFoundException("PayrollSummary not found: " + id));

        mockMvc.perform(get("/api/v1/payroll-summaries/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST validates the body and returns 201")
    void createReturns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.create(any(PayrollSummaryCreateRequest.class)))
                .thenReturn(mockDetail(id));

        String body = """
                {
                  "employeeId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "periodId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                  "assignmentId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                  "base": 1000.00,
                  "variable": 100.00,
                  "allowances": 50.00,
                  "employerTaxes": 200.00,
                  "currency": "EUR"
                }
                """;

        mockMvc.perform(post("/api/v1/payroll-summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"));
    }

    @Test
    @DisplayName("POST rejects missing base")
    void createRejectsMissingBase() throws Exception {
        String body = """
                {
                  "employeeId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                  "periodId": "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                  "assignmentId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                  "variable": 100.00,
                  "allowances": 50.00,
                  "employerTaxes": 200.00,
                  "currency": "EUR"
                }
                """;

        mockMvc.perform(post("/api/v1/payroll-summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("PUT /{id} returns the updated detail")
    void updateReturnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(UUID.class), any(PayrollSummaryUpdateRequest.class)))
                .thenReturn(mockDetail(id));

        String body = """
                {
                  "assignmentId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                  "base": 2000.00,
                  "variable": 200.00,
                  "allowances": 100.00,
                  "employerTaxes": 400.00,
                  "currency": "EUR"
                }
                """;

        mockMvc.perform(put("/api/v1/payroll-summaries/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /{id} maps ConflictException to 409")
    void updateReturns409ForValidated() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.update(any(UUID.class), any(PayrollSummaryUpdateRequest.class)))
                .thenThrow(new ConflictException("SUMMARY_NOT_DRAFT", "not draft"));

        String body = """
                {
                  "assignmentId": "cccccccc-cccc-cccc-cccc-cccccccccccc",
                  "base": 2000.00,
                  "variable": 200.00,
                  "allowances": 100.00,
                  "employerTaxes": 400.00,
                  "currency": "EUR"
                }
                """;

        mockMvc.perform(put("/api/v1/payroll-summaries/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
    }

    @Test
    @DisplayName("POST /{id}/validate returns the validated summary")
    void validateReturnsDetail() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.validate(id)).thenReturn(mockDetail(id));

        mockMvc.perform(post("/api/v1/payroll-summaries/{id}/validate", id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /validate-all returns the aggregate result")
    void validateAllReturnsResult() throws Exception {
        UUID periodId = UUID.randomUUID();
        ValidateAllResult result =
                new ValidateAllResult(3, 2, 1, List.of(
                        new ValidateAllResult.Failure(UUID.randomUUID(),
                                "ASSIGNMENT_NOT_IN_EFFECT", "not in effect")));
        when(service.validateAll(any(PayrollSummaryValidateRequest.class)))
                .thenReturn(result);

        String body = """
                { "periodId": "%s" }
                """.formatted(periodId);

        mockMvc.perform(post("/api/v1/payroll-summaries/validate-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.validated").value(2))
                .andExpect(jsonPath("$.failed").value(1));
    }

    private PayrollSummaryDetailDto mockDetail(UUID id) {
        return PayrollSummaryDetailDto.builder()
                .id(id)
                .status(PayrollSummaryStatus.DRAFT)
                .amounts(AmountsDto.builder()
                        .base(new BigDecimal("1000.00"))
                        .variable(new BigDecimal("100.00"))
                        .allowances(new BigDecimal("50.00"))
                        .employerTaxes(new BigDecimal("200.00"))
                        .total(new BigDecimal("1350.00"))
                        .currency("EUR").build())
                .build();
    }
}