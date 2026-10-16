package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.repository.criteria.RoleCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Role} reference data. */
public interface RoleRepository {

    Role save(Role role);

    Optional<Role> findById(UUID id);

    Optional<Role> findByName(String name);

    List<Role> list(RoleCriteria criteria);

    DataPage<Role> page(RoleCriteria criteria);

    boolean existsByName(String name);

    void deleteById(UUID id);
}