package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link PayrollPeriodEntity}. */
@Repository
public interface PayrollPeriodJpaRepository
        extends JpaRepository<PayrollPeriodEntity, UUID>,
        JpaSpecificationExecutor<PayrollPeriodEntity> {

    /**
     * Finds a payroll period by name.
     *
     * @param name period name
     * @return matching period, or empty
     */
    Optional<PayrollPeriodEntity> findByName(String name);

    /**
     * Finds all payroll periods that fall entirely within the given date range,
     * matching the specific status and frequency.
     *
     * @param startDate the inclusive start of the range
     * @param endDate   the inclusive end of the range
     * @param frequency the payroll period frequency (e.g., MONTHLY, BI_WEEKLY)
     * @return a list of matching payroll period entities
     */
    @Query("""
            SELECT p FROM PayrollPeriodEntity p
            WHERE p.startDate >= :startDate
              AND p.endDate <= :endDate
              AND p.frequency = :frequency
            ORDER BY p.startDate ASC
            """)
    List<PayrollPeriodEntity> findPeriodsInRangeWithStatusAndFrequency(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("frequency") PayrollPeriodFrequency frequency
    );

    /**
     * Returns whether any payroll summary belongs to this period.
     *
     * <p>Navigates the {@code @ManyToOne} to reach the period's id.</p>
     *
     * @param payrollPeriodId payroll period identifier
     * @return {@code true} when at least one summary references it
     */
    @Query("""
            SELECT COUNT(s) > 0
            FROM PayrollSummaryEntity s
            WHERE s.payrollPeriod.id = :payrollPeriodId
            """)
    boolean isReferencedByPayrollSummary(@Param("payrollPeriodId") UUID payrollPeriodId);

    /**
     * Returns whether any aggregate row belongs to this period.
     *
     * @param payrollPeriodId payroll period identifier
     * @return {@code true} when at least one aggregate references it
     */
    @Query("""
            SELECT COUNT(g) > 0
            FROM PayrollPeriodAggregateEntity g
            WHERE g.payrollPeriodId = :payrollPeriodId
            """)
    boolean isReferencedByAggregate(@Param("payrollPeriodId") UUID payrollPeriodId);

    /**
     * Returns whether any earner ranking entry belongs to this period.
     *
     * @param payrollPeriodId payroll period identifier
     * @return {@code true} when at least one ranking references it
     */
    @Query("""
            SELECT COUNT(r) > 0
            FROM PayrollPeriodEarnerRankingEntity r
            WHERE r.payrollPeriodId = :payrollPeriodId
            """)
    boolean isReferencedByRanking(@Param("payrollPeriodId") UUID payrollPeriodId);

    /**
     * Returns whether any earner percentile entry belongs to this period.
     *
     * @param payrollPeriodId payroll period identifier
     * @return {@code true} when at least one percentile references it
     */
    @Query("""
            SELECT COUNT(p) > 0
            FROM PayrollPeriodEarnerPercentileEntity p
            WHERE p.payrollPeriodId = :payrollPeriodId
            """)
    boolean isReferencedByPercentile(@Param("payrollPeriodId") UUID payrollPeriodId);

    /**
     * Returns whether the payroll period is referenced anywhere in the schema.
     *
     * @param payrollPeriodId payroll period identifier
     * @return {@code true} when at least one row references it
     */
    default boolean isReferenced(UUID payrollPeriodId) {
        return isReferencedByPayrollSummary(payrollPeriodId)
                || isReferencedByAggregate(payrollPeriodId)
                || isReferencedByRanking(payrollPeriodId)
                || isReferencedByPercentile(payrollPeriodId);
    }
}