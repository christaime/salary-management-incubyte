package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Spring Data repository for {@link RoleEntity}. */
@Repository
public interface RoleJpaRepository
        extends JpaRepository<RoleEntity, UUID>, JpaSpecificationExecutor<RoleEntity> {

    /**
     * Finds a role by name.
     *
     * @param name role name
     * @return matching role, or empty
     */
    Optional<RoleEntity> findByName(String name);

    /**
     * Returns whether a role with the given name exists.
     *
     * @param name role name
     * @return {@code true} when present
     */
    boolean existsByName(String name);
}