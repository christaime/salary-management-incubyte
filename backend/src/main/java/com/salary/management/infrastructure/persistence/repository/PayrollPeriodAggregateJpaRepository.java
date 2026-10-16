package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodAggregateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
     * Deletes all aggregates for a payroll period.
     *
     * @param payrollPeriodId payroll period identifier
     */
    void deleteByPayrollPeriodId(UUID payrollPeriodId);
}