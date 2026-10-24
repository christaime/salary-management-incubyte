package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodAggregateEntity;
import com.salary.management.infrastructure.persistence.projection.AggregateRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link PayrollPeriodAggregateEntity}. */
@Repository
public interface PayrollPeriodAggregateJpaRepository
        extends JpaRepository<PayrollPeriodAggregateEntity, UUID>,
        JpaSpecificationExecutor<PayrollPeriodAggregateEntity> {

    /**
     * Finds all aggregates for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     * @return matching aggregates
     */
    List<PayrollPeriodAggregateEntity> findByPayrollPeriodId(UUID payrollPeriodId);

    /**
     * Deletes the DRAFT aggregates for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     */
    @Modifying
    @Query("""
            DELETE FROM PayrollPeriodAggregateEntity a
            WHERE a.payrollPeriodId = :payrollPeriodId
              AND a.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.DRAFT
            """)
    void deleteDraftsByPayrollPeriodId(@Param("payrollPeriodId") UUID payrollPeriodId);

    /**
     * Groups VALIDATED payroll summaries of the given period by the five assignment
     * attributes and returns one row per group with the five summed amounts and the
     * employee count.
     *
     * <p>Read from {@code payroll_summaries}, not from the aggregate entity: the point of
     * the query is to compute the aggregates fresh for a period, not to read persisted
     * ones.</p>
     */
    @Query("""
            SELECT s.payrollPeriod.id           AS payrollPeriodId,
                   s.assignmentCountryId        AS countryId,
                   s.assignmentDepartmentId     AS departmentId,
                   s.assignmentRoleId           AS roleId,
                   s.assignmentEmploymentType   AS employmentType,
                   s.amounts.currency           AS currency,
                   COUNT(DISTINCT s.employee.id)   AS employeeCount,
                   SUM(s.amounts.base)          AS base,
                   SUM(s.amounts.variable)      AS variable,
                   SUM(s.amounts.allowances)    AS allowances,
                   SUM(s.amounts.employerTaxes) AS employerTaxes,
                   SUM(s.amounts.total)         AS total
            FROM PayrollSummaryEntity s
            WHERE s.payrollPeriod.id = :payrollPeriodId
              AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
            GROUP BY s.payrollPeriod.id, s.assignmentCountryId, s.assignmentDepartmentId,
                     s.assignmentRoleId, s.assignmentEmploymentType, s.amounts.currency
            """)
    List<AggregateRow> aggregateByGroup(@Param("payrollPeriodId") UUID payrollPeriodId);
}