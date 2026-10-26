package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.domain.repository.EmployeePeriodicAssignmentRepository;
import com.salary.management.domain.repository.criteria.EmployeePeriodicAssignmentCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import com.salary.management.infrastructure.persistence.mapper.EmployeePeriodicAssignmentPersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.CountryJpaRepository;
import com.salary.management.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.salary.management.infrastructure.persistence.repository.EmployeePeriodicAssignmentJpaRepository;
import com.salary.management.infrastructure.persistence.repository.RoleJpaRepository;
import com.salary.management.infrastructure.persistence.specification.EmployeePeriodicAssignmentSpecification;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link EmployeePeriodicAssignmentRepository}. */
@Component
@RequiredArgsConstructor
public class EmployeePeriodicAssignmentRepositoryAdapter
        implements EmployeePeriodicAssignmentRepository {

    private final EmployeePeriodicAssignmentJpaRepository jpaRepository;
    private final CountryJpaRepository countryJpaRepository;
    private final DepartmentJpaRepository departmentJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final EmployeePeriodicAssignmentPersistenceMapper mapper;

    @Override
    public EmployeePeriodicAssignment save(EmployeePeriodicAssignment assignment) {
        CountryEntity country = countryJpaRepository
                .findById(assignment.getCountry().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Country not found: " + assignment.getCountry().getId()));
        DepartmentEntity department = departmentJpaRepository
                .findById(assignment.getDepartment().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Department not found: " + assignment.getDepartment().getId()));
        RoleEntity role = roleJpaRepository
                .findById(assignment.getRole().getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Role not found: " + assignment.getRole().getId()));

        EmployeePeriodicAssignmentEntity saved =
                jpaRepository.save(mapper.toEntity(assignment));
        return mapper.toDomain(saved, country, department, role);
    }

    @Override
    public Optional<EmployeePeriodicAssignment> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    /**
     * Returns the latest in-effect assignment of {@code employeeId} whose
     * {@code startDate} falls inside {@code [startDate, endDate]} and whose
     * {@code endDate} is {@code null} or {@code <= endDate}.
     *
     * <p>When several assignments qualify, the one with the latest
     * {@code startDate} wins.</p>
     */
    @Override
    public Optional<EmployeePeriodicAssignment> findByEmployeeIdAndDateRange(
            UUID employeeId, LocalDate startDate, LocalDate endDate) {
        return jpaRepository
                .findOverlappingAssignments(employeeId, startDate, endDate)
                .stream()
                .findFirst()
                .map(this::toDomain);
    }

    @Override
    public List<EmployeePeriodicAssignment> findByEmployeeId(UUID employeeId) {
        return jpaRepository.findByEmployeeId(employeeId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<EmployeePeriodicAssignment> list(
            EmployeePeriodicAssignmentCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<EmployeePeriodicAssignmentEntity> entities = jpaRepository.findAll(
                EmployeePeriodicAssignmentSpecification.build(criteria), sort);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public DataPage<EmployeePeriodicAssignment> page(
            EmployeePeriodicAssignmentCriteria criteria) {
        Page<EmployeePeriodicAssignmentEntity> page = jpaRepository.findAll(
                EmployeePeriodicAssignmentSpecification.build(criteria),
                CriteriaUtils.toPageable(criteria));
        return toDataPage(page);
    }

    @Override
    public void deleteById(UUID id) {
        if (!jpaRepository.existsById(id)) {
            return;
        }
        if (jpaRepository.isReferenced(id)) {
            throw new DataIntegrityViolationException(
                    "EmployeePeriodicAssignment is referenced and cannot be deleted: " + id);
        }
        jpaRepository.deleteById(id);
    }

    private EmployeePeriodicAssignment toDomain(EmployeePeriodicAssignmentEntity entity) {
        CountryEntity country = countryJpaRepository
                .findById(entity.getCountryId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Country not found: " + entity.getCountryId()));
        DepartmentEntity department = departmentJpaRepository
                .findById(entity.getDepartmentId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Department not found: " + entity.getDepartmentId()));
        RoleEntity role = roleJpaRepository
                .findById(entity.getRoleId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Role not found: " + entity.getRoleId()));
        return mapper.toDomain(entity, country, department, role);
    }

    private DataPage<EmployeePeriodicAssignment> toDataPage(
            Page<EmployeePeriodicAssignmentEntity> page) {
        List<EmployeePeriodicAssignment> content =
                page.getContent().stream().map(this::toDomain).toList();
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}