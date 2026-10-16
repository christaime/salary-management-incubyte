package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayrollPeriodEarnerRankingTest {

    private static PayrollPeriod aPayrollPeriod() {
        return PayrollPeriod.builder()
                .name("2024-01 Monthly")
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2024, 1, 31))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }

    private static Country aCountry() {
        return Country.builder().isoCode("FR").name("France").currencyCode("EUR").build();
    }

    private static Department aDepartment() {
        return Department.builder().name("Engineering").build();
    }

    private static Role aRole() {
        return Role.builder().name("Engineer").build();
    }

    private static PayrollPeriodEarnerRanking valid() {
        return PayrollPeriodEarnerRanking.builder()
                .payrollPeriod(aPayrollPeriod())
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .direction(EarnerRankingDirection.TOP)
                .rank(1)
                .employeeId(UUID.randomUUID())
                .amount(new BigDecimal("12500.00"))
                .build();
    }

    @Test
    @DisplayName("Valid ranking entry is constructed and id is generated")
    void validConstruction() {
        PayrollPeriodEarnerRanking ranking = valid();
        assertNotNull(ranking.getId());
        assertEquals(EarnerRankingDirection.TOP, ranking.getDirection());
        assertEquals(1, ranking.getRank());
        assertEquals(new BigDecimal("12500.00"), ranking.getAmount());
        assertEquals("FR", ranking.getCountry().getIsoCode());
        assertEquals("Engineering", ranking.getDepartment().getName());
        assertEquals("Engineer", ranking.getRole().getName());
    }

    @Test
    @DisplayName("Rank below 1 is rejected")
    void rankBelowOneRejected() {
        assertThrows(DomainValidationException.class,
                () -> PayrollPeriodEarnerRanking.builder()
                        .payrollPeriod(aPayrollPeriod())
                        .country(aCountry())
                        .department(aDepartment())
                        .role(aRole())
                        .direction(EarnerRankingDirection.TOP)
                        .rank(0)
                        .employeeId(UUID.randomUUID())
                        .amount(BigDecimal.TEN)
                        .build());
    }

    @Test
    @DisplayName("Rank above 5 is rejected")
    void rankAboveFiveRejected() {
        assertThrows(DomainValidationException.class,
                () -> PayrollPeriodEarnerRanking.builder()
                        .payrollPeriod(aPayrollPeriod())
                        .country(aCountry())
                        .department(aDepartment())
                        .role(aRole())
                        .direction(EarnerRankingDirection.BOTTOM)
                        .rank(6)
                        .employeeId(UUID.randomUUID())
                        .amount(BigDecimal.TEN)
                        .build());
    }

    @Test
    @DisplayName("Negative amount is rejected")
    void negativeAmountRejected() {
        assertThrows(DomainValidationException.class,
                () -> PayrollPeriodEarnerRanking.builder()
                        .payrollPeriod(aPayrollPeriod())
                        .country(aCountry())
                        .department(aDepartment())
                        .role(aRole())
                        .direction(EarnerRankingDirection.TOP)
                        .rank(2)
                        .employeeId(UUID.randomUUID())
                        .amount(new BigDecimal("-1"))
                        .build());
    }

    @Test
    @DisplayName("Missing direction is rejected")
    void missingDirectionRejected() {
        assertThrows(DomainValidationException.class,
                () -> PayrollPeriodEarnerRanking.builder()
                        .payrollPeriod(aPayrollPeriod())
                        .country(aCountry())
                        .department(aDepartment())
                        .role(aRole())
                        .rank(1)
                        .employeeId(UUID.randomUUID())
                        .amount(BigDecimal.TEN)
                        .build());
    }

    @Test
    @DisplayName("Missing country is rejected")
    void missingCountryRejected() {
        assertThrows(DomainValidationException.class,
                () -> PayrollPeriodEarnerRanking.builder()
                        .payrollPeriod(aPayrollPeriod())
                        .department(aDepartment())
                        .role(aRole())
                        .direction(EarnerRankingDirection.TOP)
                        .rank(1)
                        .employeeId(UUID.randomUUID())
                        .amount(BigDecimal.TEN)
                        .build());
    }

    @Test
    @DisplayName("withRank returns a new instance and re-validates")
    void withRankTransition() {
        PayrollPeriodEarnerRanking original = valid();
        PayrollPeriodEarnerRanking updated = original.withRank(2);

        assertNotSame(original, updated);
        assertEquals(1, original.getRank());
        assertEquals(2, updated.getRank());
    }

    @Test
    @DisplayName("withRank rejects a rank outside 1..5")
    void withRankRejectsInvalid() {
        assertThrows(DomainValidationException.class, () -> valid().withRank(6));
    }
}