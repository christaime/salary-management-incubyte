package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollSummary;
import com.salary.management.domain.repository.criteria.PayrollSummaryCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link PayrollSummary} aggregates. */
public interface PayrollSummaryRepository {

    PayrollSummary save(PayrollSummary payrollSummary);

    Optional<PayrollSummary> findById(UUID id);

    Optional<PayrollSummary> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId, UUID payrollPeriodId);

    List<PayrollSummary> list(PayrollSummaryCriteria criteria);

    DataPage<PayrollSummary> page(PayrollSummaryCriteria criteria);

    void deleteById(UUID id);
}