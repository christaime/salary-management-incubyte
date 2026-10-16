package com.salary.management.domain.repository;

import com.salary.management.domain.model.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Role} reference data.
 */
public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findById(UUID id);

    List<Role> findAll();

    boolean existsByName(String name);
}