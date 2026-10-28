package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.repository.DepartmentRepository;
import com.salary.management.domain.repository.criteria.DepartmentCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.adapter.utils.DataIntegrityMessages;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.mapper.DepartmentPersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.DepartmentJpaRepository;
import com.salary.management.infrastructure.persistence.specification.DepartmentSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link DepartmentRepository}. */
@Component
@RequiredArgsConstructor
public class DepartmentRepositoryAdapter implements DepartmentRepository {

    private final DepartmentJpaRepository jpaRepository;
    private final DepartmentPersistenceMapper mapper;

    @Override
    public Department save(Department department) {
        DepartmentEntity saved = jpaRepository.save(mapper.toEntity(department));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Department> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Department> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaRepository.existsByName(name);
    }

    @Override
    public boolean existsByCode(String code) {
        return jpaRepository.existsByCode(code);
    }

    @Override
    public List<Department> list(DepartmentCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<DepartmentEntity> entities = jpaRepository.findAll(
                DepartmentSpecification.build(criteria), sort);
        return mapper.toDomainList(entities);
    }

    @Override
    public DataPage<Department> page(DepartmentCriteria criteria) {
        Page<DepartmentEntity> page = jpaRepository.findAll(
                DepartmentSpecification.build(criteria),
                CriteriaUtils.toPageable(criteria));
        return toDataPage(page);
    }

    @Override
    public void deleteById(UUID id) {
        if (!jpaRepository.existsById(id)) {
            return;
        }
        if (jpaRepository.isReferenced(id)) {
            throw DataIntegrityMessages.referenced("Department", id);
        }
        jpaRepository.deleteById(id);
    }

    private DataPage<Department> toDataPage(Page<DepartmentEntity> page) {
        List<Department> content = mapper.toDomainList(page.getContent());
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}