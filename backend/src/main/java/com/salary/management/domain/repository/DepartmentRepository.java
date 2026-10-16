package com.salary.management.domain.repository;

import com.salary.management.domain.model.Department;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Department} reference data.
 */
public interface DepartmentRepository {

    Department save(Department department);

    Optional<Department> findById(UUID id);

    List<Department> findAll();

    boolean existsByName(String name);
}