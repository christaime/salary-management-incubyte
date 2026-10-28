package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerPercentileEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayrollPeriodEarnerPercentileJpaRepository
        extends JpaRepository<PayrollPeriodEarnerPercentileEntity, UUID> {

    /**
     * Finds all percentile entries for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     * @return matching percentile entries
     */
    List<PayrollPeriodEarnerPercentileEntity> findByPayrollPeriodId(UUID payrollPeriodId);

    /**
     * Deletes the DRAFT rankings for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     */
    @Modifying
    @Query("""
            DELETE FROM PayrollPeriodEarnerPercentileEntity r
            WHERE r.payrollPeriodId = :payrollPeriodId
              AND r.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.DRAFT
            """)
    void deleteDraftsByPayrollPeriodId(@Param("payrollPeriodId") UUID payrollPeriodId);


    List<PayrollPeriodEarnerPercentileEntity> findByPayrollPeriodIdAndPercentile(
            UUID payrollPeriodId, Integer percentile);

    void deleteByPayrollPeriodId(UUID payrollPeriodId);

    long countByPayrollPeriodId(UUID payrollPeriodId);

    // ------------------------------------------------------------------
    // Dimension-scoped lookups
    // ------------------------------------------------------------------

    List<PayrollPeriodEarnerPercentileEntity> findByPayrollPeriodIdAndCountryId(
            UUID payrollPeriodId, UUID countryId);

    List<PayrollPeriodEarnerPercentileEntity> findByPayrollPeriodIdAndDepartmentId(
            UUID payrollPeriodId, UUID departmentId);

    List<PayrollPeriodEarnerPercentileEntity> findByPayrollPeriodIdAndRoleId(
            UUID payrollPeriodId, UUID roleId);

    List<PayrollPeriodEarnerPercentileEntity> findByPayrollPeriodIdAndEmploymentType(
            UUID payrollPeriodId, String employmentType);

    // ------------------------------------------------------------------
    // "Global" group: all dimension columns are null
    // ------------------------------------------------------------------

    @Query("""
            SELECT p FROM PayrollPeriodEarnerPercentileEntity p
            WHERE p.payrollPeriodId = :payrollPeriodId
              AND p.countryId        IS NULL
              AND p.departmentId     IS NULL
              AND p.roleId           IS NULL
              AND p.employmentType   IS NULL
            ORDER BY p.percentile ASC
            """)
    List<PayrollPeriodEarnerPercentileEntity> findGlobalByPayrollPeriodId(
            @Param("payrollPeriodId") UUID payrollPeriodId);

    // ------------------------------------------------------------------
    // Full 7-row distribution for one group
    // ------------------------------------------------------------------

    @Query("""
            SELECT p FROM PayrollPeriodEarnerPercentileEntity p
            WHERE p.payrollPeriodId = :payrollPeriodId
              AND ( (:countryId      IS NULL AND p.countryId      IS NULL) OR p.countryId      = :countryId )
              AND ( (:departmentId   IS NULL AND p.departmentId   IS NULL) OR p.departmentId   = :departmentId )
              AND ( (:roleId         IS NULL AND p.roleId         IS NULL) OR p.roleId         = :roleId )
              AND ( (:employmentType IS NULL AND p.employmentType IS NULL) OR p.employmentType = :employmentType )
            ORDER BY p.percentile ASC
            """)
    List<PayrollPeriodEarnerPercentileEntity> findDistribution(
            @Param("payrollPeriodId") UUID payrollPeriodId,
            @Param("countryId") UUID countryId,
            @Param("departmentId") UUID departmentId,
            @Param("roleId") UUID roleId,
            @Param("employmentType") String employmentType);

    /**
     * Returns the percentile points of one period that belong to the given
     * dimension. See
     * {@link PayrollPeriodEarnerRankingJpaRepository#findByPayrollPeriodAndDimension}
     * for the null-safe filter convention.
     */
    @Query("""
            SELECT p FROM PayrollPeriodEarnerPercentileEntity p
            WHERE p.payrollPeriodId = :payrollPeriodId
              AND ((:countryId IS NULL AND p.countryId IS NULL)
                   OR p.countryId = :countryId)
              AND ((:departmentId IS NULL AND p.departmentId IS NULL)
                   OR p.departmentId = :departmentId)
              AND ((:roleId IS NULL AND p.roleId IS NULL)
                   OR p.roleId = :roleId)
              AND ((:employmentType IS NULL AND p.employmentType IS NULL)
                   OR p.employmentType = :employmentType)
            ORDER BY p.percentile ASC
            """)
    List<PayrollPeriodEarnerPercentileEntity> findByPayrollPeriodAndDimension(
            @Param("payrollPeriodId") UUID payrollPeriodId,
            @Param("countryId") UUID countryId,
            @Param("departmentId") UUID departmentId,
            @Param("roleId") UUID roleId,
            @Param("employmentType") String employmentType);
}