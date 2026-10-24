package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollSummaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link PayrollSummaryEntity}. */
@Repository
public interface PayrollSummaryJpaRepository
        extends JpaRepository<PayrollSummaryEntity, UUID>,
        JpaSpecificationExecutor<PayrollSummaryEntity> {

    /**
     * Finds a summary by employee and payroll period.
     *
     * @param employeeId      employee identifier
     * @param payrollPeriodId payroll period identifier
     * @return matching summary, or empty
     */
    Optional<PayrollSummaryEntity> findByEmployeeIdAndPayrollPeriodId(
            UUID employeeId,
            UUID payrollPeriodId);
}