package com.salary.management.domain.repository;

import com.salary.management.domain.model.PayrollPeriodAggregate;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.values.GroupedSummary;
import com.salary.management.domain.repository.criteria.PayrollPeriodAggregateCriteria;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Persistence port for {@link PayrollPeriodAggregate} rows. */
public interface PayrollPeriodAggregateRepository {

    void saveAll(List<PayrollPeriodAggregate> aggregates);

    List<PayrollPeriodAggregate> findByPayrollPeriodId(UUID payrollPeriodId);

    List<PayrollPeriodAggregate> list(PayrollPeriodAggregateCriteria criteria);

    /**
     * Deletes the DRAFT aggregates for a payroll period, leaving VALIDATED rows untouched.
     *
     * <p>Used before persisting a freshly built snapshot for an open period.</p>
     *
     * @param payrollPeriodId payroll period identifier
     */
    void deleteDraftsByPayrollPeriodId(UUID payrollPeriodId);
    /**
     * Groups VALIDATED payroll summaries of the given period by the five assignment
     * attributes. Amounts are in each group's currency.
     */
    List<GroupedSummary> aggregateByGroup(UUID payrollPeriodId, LocalDate startDate, LocalDate endDate);

    // Get the sum of the total reference amount in the aggregates of the period
    BigDecimal sumReferenceTotalByPeriodId(UUID payrollPeriodId);

    // Get the sum of the total reference amount in the aggregates of the period, corresponding to the correct dimension
    BigDecimal sumReferenceTotalByPeriodAndDimension(UUID payrollPeriodId, UUID countryId, UUID departmentId, UUID roleId, EmploymentType employmentType);
}