package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link DepartmentEntity}. */
@Repository
public interface DepartmentJpaRepository
        extends JpaRepository<DepartmentEntity, UUID>,
        JpaSpecificationExecutor<DepartmentEntity> {

    /**
     * Finds a department by name.
     *
     * @param name department name
     * @return matching department, or empty
     */
    Optional<DepartmentEntity> findByName(String name);

    /**
     * Returns whether a department with the given name exists.
     *
     * @param name department name
     * @return {@code true} when present
     */
    boolean existsByName(String name);
}