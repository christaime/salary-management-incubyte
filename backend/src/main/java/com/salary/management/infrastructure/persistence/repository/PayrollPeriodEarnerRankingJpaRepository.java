package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.projection.RankedRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Top {@code limit} employees by total DESC per group, over VALIDATED summaries of the
     * given period. The subselect computes the rank; the outer query filters on it, because
     * neither JPQL nor SQLite supports {@code QUALIFY}.
     */
    @Query("""
            SELECT r.payrollPeriodId   AS payrollPeriodId,
                   r.countryId         AS countryId,
                   r.departmentId      AS departmentId,
                   r.roleId            AS roleId,
                   r.employmentType    AS employmentType,
                   r.rank              AS rank,
                   r.employeeId        AS employeeId,
                   r.total             AS total
            FROM (
                SELECT s.payrollPeriod.id           AS payrollPeriodId,
                       s.assignmentCountryId        AS countryId,
                       s.assignmentDepartmentId     AS departmentId,
                       s.assignmentRoleId           AS roleId,
                       s.assignmentEmploymentType   AS employmentType,
                       s.employee.id                AS employeeId,
                       s.amounts.total              AS total,
                       CAST(ROW_NUMBER() OVER (
                           PARTITION BY s.assignmentCountryId, s.assignmentDepartmentId,
                                        s.assignmentRoleId, s.assignmentEmploymentType
                           ORDER BY s.amounts.total DESC, s.employee.id ASC
                       ) AS INTEGER)                AS rank
                FROM PayrollSummaryEntity s
                WHERE s.payrollPeriod.id = :payrollPeriodId
                  AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
            ) r
            WHERE r.rank <= :limit
            """)
    List<RankedRow> topByGroup(
            @Param("payrollPeriodId") UUID payrollPeriodId,
            @Param("limit") int limit);

    /**
     * Bottom {@code limit} employees by total ASC per group, over VALIDATED summaries of
     * the given period. Same subselect shape as {@link #topByGroup(UUID, int)}, only the
     * ORDER BY direction inside the window changes.
     */
    @Query("""
            SELECT r.payrollPeriodId   AS payrollPeriodId,
                   r.countryId         AS countryId,
                   r.departmentId      AS departmentId,
                   r.roleId            AS roleId,
                   r.employmentType    AS employmentType,
                   r.rank              AS rank,
                   r.employeeId        AS employeeId,
                   r.total             AS total
            FROM (
                SELECT s.payrollPeriod.id           AS payrollPeriodId,
                       s.assignmentCountryId        AS countryId,
                       s.assignmentDepartmentId     AS departmentId,
                       s.assignmentRoleId           AS roleId,
                       s.assignmentEmploymentType   AS employmentType,
                       s.employee.id                AS employeeId,
                       s.amounts.total              AS total,
                       CAST(ROW_NUMBER() OVER (
                           PARTITION BY s.assignmentCountryId, s.assignmentDepartmentId,
                                        s.assignmentRoleId, s.assignmentEmploymentType
                           ORDER BY s.amounts.total ASC, s.employee.id ASC
                       ) AS INTEGER)                AS rank
                FROM PayrollSummaryEntity s
                WHERE s.payrollPeriod.id = :payrollPeriodId
                  AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
            ) r
            WHERE r.rank <= :limit
            """)
    List<RankedRow> bottomByGroup(
            @Param("payrollPeriodId") UUID payrollPeriodId,
            @Param("limit") int limit);
}