package com.salary.management.domain.repository;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port for {@link Employee} aggregates. */
public interface EmployeeRepository {

    Employee save(Employee employee);

    Optional<Employee> findById(UUID id);

    Optional<Employee> findByMatricule(String matricule);

    List<Employee> list(EmployeeCriteria criteria);

    DataPage<Employee> page(EmployeeCriteria criteria);

    boolean existsByMatricule(String matricule);

    void deleteById(UUID id);
}