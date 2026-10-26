package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodAggregateEntity;
import com.salary.management.infrastructure.persistence.projection.AggregateRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
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
     * Groups VALIDATED payroll summaries across all periods whose {@code startDate}
     * falls within the given date range, by the five assignment attributes, and
     * returns one row per dimension group with the summed amounts and employee count.
     *
     * <p>The result is <strong>rolled up across all matching periods</strong>: rows
     * are aggregated by the dimension tuple only, so the same (country, department,
     * role, employment type, currency) group appearing in multiple periods of the
     * range collapses into a single row whose amounts are the total across all those
     * periods. If the caller needs per-period attribution, add {@code p.id} to both
     * the {@code SELECT} and the {@code GROUP BY}.</p>
     *
     * <p>Periods are included only if their {@code startDate} is inside the range.
     * A period that started before {@code startDate} is excluded even if it overlaps
     * the window.</p>
     *
     * <p>Reads from {@code payroll_summaries}, not from the persisted aggregate
     * entity: the point of the query is to compute the aggregates fresh from source
     * data, not to read a previously computed snapshot.</p>
     *
     * @param startDate inclusive lower bound on {@code PayrollPeriodEntity.startDate}
     * @param endDate   inclusive upper bound on {@code PayrollPeriodEntity.startDate}
     * @return one row per distinct
     *         (country, department, role, employment type, currency) tuple, with
     *         summed amounts and the distinct-employee count across the range
     */
    @Query("""
        SELECT s.assignmentCountryId        AS countryId,
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
        INNER JOIN PayrollPeriodEntity p ON p.id = s.payrollPeriod.id
        WHERE p.startDate >= :startDate
          AND p.startDate < :endDate
          AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
        GROUP BY s.assignmentCountryId, s.assignmentDepartmentId,
                 s.assignmentRoleId, s.assignmentEmploymentType, s.amounts.currency
        """)
    List<AggregateRow> aggregateByGroup(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * Returns the sum of reference totals across all aggregate rows of one period.
     *
     * @param periodId payroll period identifier
     * @return sum of {@code referenceAmounts.total}, or zero when the period has no aggregates
     */
    @Query("""
            SELECT COALESCE(SUM(a.referenceAmounts.total), 0)
            FROM PayrollPeriodAggregateEntity a
            WHERE a.payrollPeriodId = :periodId
            """)
    BigDecimal sumReferenceTotalByPeriodId(@Param("periodId") UUID periodId);

    /**
     * Returns the sum of reference totals across the aggregate rows of one period,
     * restricted to the rows matching the given dimension. A {@code null} parameter
     * means "do not filter on this column".
     *
     * @param periodId       payroll period identifier
     * @param countryId      country filter, or {@code null}
     * @param departmentId   department filter, or {@code null}
     * @param roleId         role filter, or {@code null}
     * @param employmentType employment type filter, or {@code null}
     * @return sum of {@code referenceAmounts.total}, or zero when no row matches
     */
    @Query("""
            SELECT COALESCE(SUM(a.referenceAmounts.total), 0)
            FROM PayrollPeriodAggregateEntity a
            WHERE a.payrollPeriodId = :periodId
              AND (:countryId      IS NULL OR a.countryId      = :countryId)
              AND (:departmentId   IS NULL OR a.departmentId   = :departmentId)
              AND (:roleId         IS NULL OR a.roleId         = :roleId)
              AND (:employmentType IS NULL OR a.employmentType = :employmentType)
            """)
    BigDecimal sumReferenceTotalByPeriodAndDimension(
            @Param("periodId") UUID periodId,
            @Param("countryId") UUID countryId,
            @Param("departmentId") UUID departmentId,
            @Param("roleId") UUID roleId,
            @Param("employmentType") EmploymentType employmentType);
}