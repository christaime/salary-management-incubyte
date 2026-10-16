package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
}