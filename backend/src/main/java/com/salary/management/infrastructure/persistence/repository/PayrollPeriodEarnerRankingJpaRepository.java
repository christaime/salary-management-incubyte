package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
     * Deletes all ranking entries for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     */
    void deleteByPayrollPeriodId(UUID payrollPeriodId);
}