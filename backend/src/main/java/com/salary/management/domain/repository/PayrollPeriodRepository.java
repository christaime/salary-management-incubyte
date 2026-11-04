package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link PayrollPeriod} aggregates. */
public interface PayrollPeriodRepository {

    PayrollPeriod save(PayrollPeriod payrollPeriod);

    Optional<PayrollPeriod> findById(UUID id);

    Optional<PayrollPeriod> findByName(String name);

    List<PayrollPeriod> list(PayrollPeriodCriteria criteria);

    DataPage<PayrollPeriod> page(PayrollPeriodCriteria criteria);

    void deleteById(UUID id);

    /**
     * Returns {@code true} if any payroll period of the given frequency overlaps the
     * half-open range {@code [startDate, endDate)}, ignoring the period identified by
     * {@code excludingId}.
     *
     * <p>Overlap is {@code existing.startDate < endDate && startDate < existing.endDate}.</p>
     *
     * @param frequency   frequency to restrict the check to
     * @param startDate   inclusive lower bound of the candidate range
     * @param endDate     exclusive upper bound of the candidate range
     * @param excludingId period to ignore (the one being edited), or {@code null}
     * @return {@code true} when an overlapping period exists
     */
    boolean existsOverlapping(
            PayrollPeriodFrequency frequency,
            LocalDate startDate,
            LocalDate endDate,
            UUID excludingId);

    /**
     * Marks the monthly period and its bi-weekly sub-periods (those whose
     * {@code startDate} is in {@code [startDate, endDate)}) as closed.
     *
     * @param monthlyId monthly period identifier
     * @param startDate monthly period start date, inclusive
     * @param endDate   monthly period end date, exclusive for the bi-weekly match
     * @return number of rows set to closed
     */
    int closeMonthlyAndSubPeriods(UUID monthlyId, LocalDate startDate, LocalDate endDate);

    List<PayrollPeriod> findMonthlyAndSubPeriods(UUID monthlyId, LocalDate startDate, LocalDate endDate);

    boolean isReferenced(UUID payrollPeriodId);
}