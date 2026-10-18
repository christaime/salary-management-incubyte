package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.projection.RankedRow;
import com.salary.management.infrastructure.persistence.projection.StatisticsRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link PayrollPeriodEarnerRankingEntity}. */
@Repository
public interface PayrollPeriodEarnerRankingJpaRepository
        extends JpaRepository<PayrollPeriodEarnerRankingEntity, UUID>,
        JpaSpecificationExecutor<PayrollPeriodEarnerRankingEntity> {

    /**
     * Finds all ranking entries for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     * @return matching ranking entries
     */
    List<PayrollPeriodEarnerRankingEntity> findByPayrollPeriodId(UUID payrollPeriodId);

    /**
     * Deletes the DRAFT rankings for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     */
    @Modifying
    @Query("""
            DELETE FROM PayrollPeriodEarnerRankingEntity r
            WHERE r.payrollPeriodId = :payrollPeriodId
              AND r.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.DRAFT
            """)
    void deleteDraftsByPayrollPeriodId(@Param("payrollPeriodId") UUID payrollPeriodId);

    /**
     * Top {@code limit} employees per group by reference amount DESC, over VALIDATED
     * summaries of the given period.
     *
     * <p>Reference amounts are computed as
     * {@code total * referenceAmount / rate} against {@code :referenceCurrencyCode}.
     * Summaries in the reference currency itself are used unchanged. The {@code LEFT JOIN}
     * keeps the reference-currency rows, which have no {@code currency_rates} entry; the
     * {@code referenceTotal IS NOT NULL} filter drops rows whose currency has no configured
     * rate, before the window function runs. That guarantees the top-N is filled from the
     * set of rows that could actually be converted, not padded by rows that would later be
     * dropped.</p>
     *
     * @param payrollPeriodId     payroll period identifier
     * @param referenceCurrencyCode reference currency code
     * @param limit               maximum entries per group
     * @return ranked rows
     */
    @Query("""
            SELECT r.payrollPeriodId   AS payrollPeriodId,
                   r.countryId         AS countryId,
                   r.departmentId      AS departmentId,
                   r.roleId            AS roleId,
                   r.employmentType    AS employmentType,
                   r.rank              AS rank,
                   r.employeeId        AS employeeId,
                   r.originalTotal     AS originalTotal,
                   r.originalCurrency  AS originalCurrency,
                   r.referenceTotal    AS referenceTotal,
                   r.referenceCurrency AS referenceCurrency
            FROM (
                SELECT q.payrollPeriodId   AS payrollPeriodId,
                       q.countryId         AS countryId,
                       q.departmentId      AS departmentId,
                       q.roleId            AS roleId,
                       q.employmentType    AS employmentType,
                       q.employeeId        AS employeeId,
                       q.originalTotal     AS originalTotal,
                       q.originalCurrency  AS originalCurrency,
                       q.referenceTotal    AS referenceTotal,
                       q.referenceCurrency  AS referenceCurrency,
                       CAST(ROW_NUMBER() OVER (
                           PARTITION BY q.countryId, q.departmentId,
                                        q.roleId, q.employmentType
                           ORDER BY q.referenceTotal DESC, q.employeeId ASC
                       ) AS INTEGER)       AS rank
                FROM (
                    SELECT s.payrollPeriod.id           AS payrollPeriodId,
                           s.assignmentCountryId        AS countryId,
                           s.assignmentDepartmentId     AS departmentId,
                           s.assignmentRoleId           AS roleId,
                           s.assignmentEmploymentType   AS employmentType,
                           s.employee.id                AS employeeId,
                           s.amounts.total              AS originalTotal,
                           s.amounts.currency           AS originalCurrency,
                           CASE
                             WHEN s.amounts.currency = :referenceCurrencyCode
                               THEN s.amounts.total
                             ELSE s.amounts.total * cr.referenceAmount / cr.rate
                           END                          AS referenceTotal,
                           :referenceCurrencyCode       AS referenceCurrency
                    FROM PayrollSummaryEntity s
                    LEFT JOIN CurrencyRateEntity cr
                      ON cr.currencyCode = s.amounts.currency
                     AND cr.referenceCurrencyCode = :referenceCurrencyCode
                    WHERE s.payrollPeriod.id = :payrollPeriodId
                      AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
                ) q
                WHERE q.referenceTotal IS NOT NULL
            ) r
            WHERE r.rank <= :limit
            """)
    List<RankedRow> topByGroup(
            @Param("payrollPeriodId") UUID payrollPeriodId,
            @Param("referenceCurrencyCode") String referenceCurrencyCode,
            @Param("limit") int limit);

    /**
     * Bottom {@code limit} employees per group by reference amount ASC, over VALIDATED
     * summaries of the given period. Same shape as
     * {@link #topByGroup(UUID, String, int)}, only the ordering direction inside the window
     * changes.
     *
     * @param payrollPeriodId       payroll period identifier
     * @param referenceCurrencyCode reference currency code
     * @param limit                 maximum entries per group
     * @return ranked rows
     */
    @Query("""
            SELECT r.payrollPeriodId   AS payrollPeriodId,
                   r.countryId         AS countryId,
                   r.departmentId      AS departmentId,
                   r.roleId            AS roleId,
                   r.employmentType    AS employmentType,
                   r.rank              AS rank,
                   r.employeeId        AS employeeId,
                   r.originalTotal     AS originalTotal,
                   r.originalCurrency  AS originalCurrency,
                   r.referenceTotal    AS referenceTotal,
                   r.referenceCurrency AS referenceCurrency
            FROM (
                SELECT q.payrollPeriodId   AS payrollPeriodId,
                       q.countryId         AS countryId,
                       q.departmentId      AS departmentId,
                       q.roleId            AS roleId,
                       q.employmentType    AS employmentType,
                       q.employeeId        AS employeeId,
                       q.originalTotal     AS originalTotal,
                       q.originalCurrency  AS originalCurrency,
                       q.referenceTotal    AS referenceTotal,
                       q.referenceCurrency  AS referenceCurrency,
                       CAST(ROW_NUMBER() OVER (
                           PARTITION BY q.countryId, q.departmentId,
                                        q.roleId, q.employmentType
                           ORDER BY q.referenceTotal ASC, q.employeeId ASC
                       ) AS INTEGER)       AS rank
                FROM (
                    SELECT s.payrollPeriod.id           AS payrollPeriodId,
                           s.assignmentCountryId        AS countryId,
                           s.assignmentDepartmentId     AS departmentId,
                           s.assignmentRoleId           AS roleId,
                           s.assignmentEmploymentType   AS employmentType,
                           s.employee.id                AS employeeId,
                           s.amounts.total              AS originalTotal,
                           s.amounts.currency           AS originalCurrency,
                           CASE
                             WHEN s.amounts.currency = :referenceCurrencyCode
                               THEN s.amounts.total
                             ELSE s.amounts.total * cr.referenceAmount / cr.rate
                           END                          AS referenceTotal,
                           :referenceCurrencyCode       AS referenceCurrency
                    FROM PayrollSummaryEntity s
                    LEFT JOIN CurrencyRateEntity cr
                      ON cr.currencyCode = s.amounts.currency
                     AND cr.referenceCurrencyCode = :referenceCurrencyCode
                    WHERE s.payrollPeriod.id = :payrollPeriodId
                      AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
                ) q
                WHERE q.referenceTotal IS NOT NULL
            ) r
            WHERE r.rank <= :limit
            """)
    List<RankedRow> bottomByGroup(
            @Param("payrollPeriodId") UUID payrollPeriodId,
            @Param("referenceCurrencyCode") String referenceCurrencyCode,
            @Param("limit") int limit);


    /**
     * Per-group minimum and maximum of the ranked population, converted to the given
     * reference currency.
     *
     * <p>Reads {@code PayrollPeriodEarnerRankingEntity}. Rows whose original currency is the
     * reference currency are used as-is; rows in any other currency are converted with
     * {@code amount * reference_amount / rate}, using the {@code LEFT JOIN} on
     * {@code currency_rates}. Rows whose currency has no rate are excluded by the
     * {@code referenceTotal IS NOT NULL} filter.</p>
     *
     * <p>No {@code rank} filter is applied: {@code MIN} and {@code MAX} are taken over the
     * whole ranked population of the group. The ranked population contains the top-5 and
     * bottom-5 of the group by construction, so its {@code MIN} and {@code MAX} are the
     * group's true extremes.</p>
     *
     * @param periodIds             periods to include
     * @param referenceCurrencyCode reference currency code
     * @param countryIds            country filter, or {@code null}
     * @param departmentIds         department filter, or {@code null}
     * @param roleIds               role filter, or {@code null}
     * @param employmentTypes       employment type filter, or {@code null}
     * @return one row per distinct (country, department, role, employmentType, currency)
     *     with {@code minReferenceAmount} and {@code maxReferenceAmount}
     */
    @Query("""
            SELECT c.isoCode                AS countryCode,
                   c.name                   AS countryName,
                   d.id                     AS departmentCode,
                   d.name                   AS departmentName,
                   ro.id                    AS roleCode,
                   ro.name                  AS roleName,
                   r.employmentType         AS employmentType,
                   r.originalCurrency       AS currency,
                   MIN(r.referenceTotal)    AS minReferenceAmount,
                   MAX(r.referenceTotal)    AS maxReferenceAmount
            FROM (
                SELECT s.countryId           AS countryId,
                       s.departmentId        AS departmentId,
                       s.roleId              AS roleId,
                       s.employmentType      AS employmentType,
                       s.originalAmount.currency AS originalCurrency,
                       CASE
                         WHEN s.originalAmount.currency = :referenceCurrencyCode
                           THEN s.originalAmount.amount
                         ELSE s.originalAmount.amount * cr.referenceAmount / cr.rate
                       END                   AS referenceTotal
                FROM PayrollPeriodEarnerRankingEntity s
                LEFT JOIN CurrencyRateEntity cr
                  ON cr.currencyCode = s.originalAmount.currency
                 AND cr.referenceCurrencyCode = :referenceCurrencyCode
                WHERE s.payrollPeriodId IN :periodIds
                  AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
                  AND (:countryIds IS NULL OR s.countryId IN :countryIds)
                  AND (:departmentIds IS NULL OR s.departmentId IN :departmentIds)
                  AND (:roleIds IS NULL OR s.roleId IN :roleIds)
                  AND (:employmentTypes IS NULL OR s.employmentType IN :employmentTypes)
            ) r
            JOIN CountryEntity c    ON c.id = r.countryId
            JOIN DepartmentEntity d ON d.id = r.departmentId
            JOIN RoleEntity ro      ON ro.id = r.roleId
            WHERE r.referenceTotal IS NOT NULL
            GROUP BY c.isoCode, c.name, d.id, d.name, ro.id, ro.name,
                     r.employmentType, r.originalCurrency
            """)
    List<StatisticsRow> minMaxByGroup(
            @Param("periodIds") List<UUID> periodIds,
            @Param("referenceCurrencyCode") String referenceCurrencyCode,
            @Param("countryIds") Collection<UUID> countryIds,
            @Param("departmentIds") Collection<UUID> departmentIds,
            @Param("roleIds") Collection<UUID> roleIds,
            @Param("employmentTypes")
            Collection<com.salary.management.domain.model.enums.EmploymentType> employmentTypes);
}