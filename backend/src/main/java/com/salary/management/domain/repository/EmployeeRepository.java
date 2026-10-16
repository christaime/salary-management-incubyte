package com.salary.management.domain.repository;

import com.salary.management.domain.model.Employee;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link Employee} aggregates.
 */
public interface EmployeeRepository {

    Employee save(Employee employee);

    Optional<Employee> findById(UUID id);

    Optional<Employee> findByMatricule(String matricule);

    boolean existsByMatricule(String matricule);

    void deleteById(UUID id);
}