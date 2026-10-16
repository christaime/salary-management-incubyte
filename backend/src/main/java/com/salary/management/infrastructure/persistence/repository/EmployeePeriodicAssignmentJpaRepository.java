package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Spring Data repository for {@link EmployeePeriodicAssignmentEntity}. */
@Repository
public interface EmployeePeriodicAssignmentJpaRepository
        extends JpaRepository<EmployeePeriodicAssignmentEntity, UUID>,
        JpaSpecificationExecutor<EmployeePeriodicAssignmentEntity> {

    /**
     * Finds all assignments for an employee.
     *
     * @param employeeId employee identifier
     * @return matching assignments
     */
    List<EmployeePeriodicAssignmentEntity> findByEmployeeId(UUID employeeId);
}