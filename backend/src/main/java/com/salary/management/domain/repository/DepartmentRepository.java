package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.repository.criteria.DepartmentCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Department} reference data. */
public interface DepartmentRepository {

    Department save(Department department);

    Optional<Department> findById(UUID id);

    Optional<Department> findByName(String name);

    List<Department> list(DepartmentCriteria criteria);

    DataPage<Department> page(DepartmentCriteria criteria);

    boolean existsByName(String name);

    void deleteById(UUID id);
}