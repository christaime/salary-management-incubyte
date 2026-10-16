package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link EmployeeEntity}. */
@Repository
public interface EmployeeJpaRepository
        extends JpaRepository<EmployeeEntity, UUID>, JpaSpecificationExecutor<EmployeeEntity> {

    /**
     * Finds an employee by matricule.
     *
     * @param matricule business key
     * @return matching employee, or empty
     */
    Optional<EmployeeEntity> findByMatricule(String matricule);

    /**
     * Returns whether an employee with the given matricule exists.
     *
     * @param matricule business key
     * @return {@code true} when present
     */
    boolean existsByMatricule(String matricule);
}