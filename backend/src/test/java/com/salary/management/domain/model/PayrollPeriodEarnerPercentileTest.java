package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.Amount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tests for {@link PayrollPeriodEarnerPercentile}. */
class PayrollPeriodEarnerPercentileTest {

    @Test
    @DisplayName("valid build succeeds and generates an id")
    void validBuildSucceedsAndGeneratesId() {
        PayrollPeriodEarnerPercentile percentile = valid().build();

        assertNotNull(percentile.getId());
        assertEquals(50, percentile.getPercentile());
        assertEquals(PayrollSummaryStatus.DRAFT, percentile.getStatus());
        assertEquals(EmploymentType.FULL_TIME, percentile.getEmploymentType());
        assertEquals(10, percentile.getEmployeeCount());
    }

    @Test
    @DisplayName("null payrollPeriod is rejected")
    void nullPayrollPeriodIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().payrollPeriod(null).build());
    }

    @Test
    @DisplayName("null status is rejected")
    void nullStatusIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().status(null).build());
    }

    @Test
    @DisplayName("null percentile is rejected")
    void nullPercentileIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().percentile(null).build());
    }

    @Test
    @DisplayName("percentile below 0 is rejected")
    void percentileBelowZeroIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().percentile(-1).build());
    }

    @Test
    @DisplayName("percentile above 100 is rejected")
    void percentileAboveHundredIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().percentile(101).build());
    }

    @Test
    @DisplayName("null referenceAmount is rejected")
    void nullReferenceAmountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().referenceAmount(null).build());
    }

    @Test
    @DisplayName("null employeeCount is rejected")
    void nullEmployeeCountIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().employeeCount(null).build());
    }

    @Test
    @DisplayName("employeeCount below 1 is rejected")
    void employeeCountBelowOneIsRejected() {
        assertThrows(DomainValidationException.class,
                () -> valid().employeeCount(0).build());
    }

    @Test
    @DisplayName("all supported percentile points are accepted")
    void allSupportedPercentilePointsAreAccepted() {
        for (int p : new int[]{0, 10, 25, 50, 75, 90, 100}) {
            PayrollPeriodEarnerPercentile percentile =
                    valid().percentile(p).build();
            assertEquals(p, percentile.getPercentile());
        }
    }

    @Test
    @DisplayName("global group: all dimension references are null")
    void globalGroupHasAllDimensionsNull() {
        PayrollPeriodEarnerPercentile global = PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.VALIDATED)
                .percentile(50)
                .referenceAmount(amount("85000.00", "USD"))
                .employeeCount(42)
                .build();

        assertNotNull(global.getId());
        assertEquals(null, global.getCountry());
        assertEquals(null, global.getDepartment());
        assertEquals(null, global.getRole());
        assertEquals(null, global.getEmploymentType());
        assertEquals(42, global.getEmployeeCount());
    }

    @Test
    @DisplayName("scoped group: exactly one dimension reference is set")
    void scopedGroupHasExactlyOneDimension() {
        PayrollPeriodEarnerPercentile scoped = PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.VALIDATED)
                .country(aCountry())
                .percentile(90)
                .referenceAmount(amount("120000.00", "USD"))
                .employeeCount(28)
                .build();

        assertNotNull(scoped.getCountry());
        assertEquals(null, scoped.getDepartment());
        assertEquals(null, scoped.getRole());
        assertEquals(null, scoped.getEmploymentType());
        assertEquals(28, scoped.getEmployeeCount());
    }

    // ------------------------------------------------------------------
    // Copy methods
    // ------------------------------------------------------------------

    @Test
    @DisplayName("withReferenceAmount returns a new instance with the same id")
    void withReferenceAmountReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerPercentile original = valid().build();
        PayrollPeriodEarnerPercentile updated =
                original.withReferenceAmount(amount("150000.00", "USD"));

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(0, original.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("85000.00")));
        assertEquals(0, updated.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("150000.00")));
    }

    @Test
    @DisplayName("withEmployeeCount returns a new instance with the same id")
    void withEmployeeCountReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerPercentile original = valid().build();
        PayrollPeriodEarnerPercentile updated = original.withEmployeeCount(20);

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(10, original.getEmployeeCount());
        assertEquals(20, updated.getEmployeeCount());
    }

    @Test
    @DisplayName("withStatus returns a new instance with the same id")
    void withStatusReturnsNewInstanceWithSameId() {
        PayrollPeriodEarnerPercentile original = valid().build();
        PayrollPeriodEarnerPercentile updated = original.withStatus(PayrollSummaryStatus.VALIDATED);

        assertNotSame(original, updated);
        assertEquals(original.getId(), updated.getId());
        assertEquals(PayrollSummaryStatus.DRAFT, original.getStatus());
        assertEquals(PayrollSummaryStatus.VALIDATED, updated.getStatus());
    }

    // ------------------------------------------------------------------
    // Happy path
    // ------------------------------------------------------------------

    @Test
    @DisplayName("happy path with realistic values")
    void happyPathWithRealisticValues() {
        PayrollPeriodEarnerPercentile percentile = valid().build();

        assertEquals("FR", percentile.getCountry().getIsoCode());
        assertEquals("Engineering", percentile.getDepartment().getName());
        assertEquals("Senior Engineer", percentile.getRole().getName());
        assertEquals(EmploymentType.FULL_TIME, percentile.getEmploymentType());
        assertEquals(50, percentile.getPercentile());
        assertEquals(0, percentile.getReferenceAmount().getAmount()
                .compareTo(new BigDecimal("85000.00")));
        assertEquals("USD", percentile.getReferenceAmount().getCurrency());
        assertEquals(10, percentile.getEmployeeCount());
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private PayrollPeriodEarnerPercentile.Builder valid() {
        return PayrollPeriodEarnerPercentile.builder()
                .payrollPeriod(aPayrollPeriod())
                .status(PayrollSummaryStatus.DRAFT)
                .country(aCountry())
                .department(aDepartment())
                .role(aRole())
                .employmentType(EmploymentType.FULL_TIME)
                .percentile(50)
                .referenceAmount(amount("85000.00", "USD"))
                .employeeCount(10);
    }

    private Amount amount(String value, String currency) {
        return Amount.builder()
                .amount(new BigDecimal(value))
                .currency(currency)
                .build();
    }

    private Country aCountry() {
        return Country.builder()
                .isoCode("FR")
                .name("France")
                .currencyCode("EUR")
                .build();
    }

    private Department aDepartment() {
        return Department.builder()
                .code("ENG")
                .name("Engineering")
                .build();
    }

    private Role aRole() {
        return Role.builder()
                .code("SSWE")
                .name("Senior Engineer")
                .build();
    }

    private PayrollPeriod aPayrollPeriod() {
        return PayrollPeriod.builder()
                .name("September 2026")
                .startDate(LocalDate.of(2026, 9, 1))
                .endDate(LocalDate.of(2026, 9, 30))
                .frequency(PayrollPeriodFrequency.MONTHLY)
                .closed(false)
                .build();
    }
}