package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Returns whether the payroll period is referenced anywhere in the schema.
     *
     * @param payrollPeriodId payroll period identifier
     * @return {@code true} when at least one row references it
     */
    default boolean isReferenced(UUID payrollPeriodId) {
        return isReferencedByPayrollSummary(payrollPeriodId)
                || isReferencedByAggregate(payrollPeriodId)
                || isReferencedByRanking(payrollPeriodId);
    }
}