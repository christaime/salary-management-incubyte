package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Employee;
import com.salary.management.domain.repository.EmployeeRepository;
import com.salary.management.domain.repository.criteria.EmployeeCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.adapter.utils.DataIntegrityMessages;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import com.salary.management.infrastructure.persistence.mapper.EmployeePersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.CountryJpaRepository;
import com.salary.management.infrastructure.persistence.repository.EmployeeJpaRepository;
import com.salary.management.infrastructure.persistence.specification.EmployeeSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link EmployeeRepository}. */
@Component
@RequiredArgsConstructor
public class EmployeeRepositoryAdapter implements EmployeeRepository {

    private final EmployeeJpaRepository jpaRepository;
    private final CountryJpaRepository countryJpaRepository;
    private final EmployeePersistenceMapper mapper;

    @Override
    public Employee save(Employee employee) {
        if (employee.getOriginCountry() != null) {
            UUID originCountryId = employee.getOriginCountry().getId();
            if (!countryJpaRepository.existsById(originCountryId)) {
                throw new EntityNotFoundException(
                        "Country not found: " + originCountryId);
            }
        }
        EmployeeEntity saved = jpaRepository.save(mapper.toEntity(employee));
        return toDomain(saved);
    }

    @Override
    public Optional<Employee> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Employee> findByMatricule(String matricule) {
        return jpaRepository.findByMatricule(matricule).map(this::toDomain);
    }

    @Override
    public List<Employee> list(EmployeeCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<EmployeeEntity> entities = jpaRepository.findAll(
                EmployeeSpecification.build(criteria), sort);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public DataPage<Employee> page(EmployeeCriteria criteria) {
        Page<EmployeeEntity> page = jpaRepository.findAll(
                EmployeeSpecification.build(criteria),
                CriteriaUtils.toPageable(criteria));
        return toDataPage(page);
    }

    @Override
    public boolean existsByMatricule(String matricule) {
        return jpaRepository.existsByMatricule(matricule);
    }

    @Override
    public void deleteById(UUID id) {
        if (!jpaRepository.existsById(id)) {
            return;
        }
        if (jpaRepository.isReferenced(id)) {
            throw DataIntegrityMessages.referenced("Employee", id);
        }
        jpaRepository.deleteById(id);
    }

    private Employee toDomain(EmployeeEntity entity) {
        CountryEntity originCountry = entity.getOriginCountryId() == null
                ? null
                : countryJpaRepository.findById(entity.getOriginCountryId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Country not found: " + entity.getOriginCountryId()));
        return mapper.toDomain(entity, originCountry);
    }

    private DataPage<Employee> toDataPage(Page<EmployeeEntity> page) {
        List<Employee> content = page.getContent().stream().map(this::toDomain).toList();
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}