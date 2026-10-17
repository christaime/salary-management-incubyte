package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.repository.RoleRepository;
import com.salary.management.domain.repository.criteria.RoleCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import com.salary.management.infrastructure.persistence.mapper.RolePersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.RoleJpaRepository;
import com.salary.management.infrastructure.persistence.specification.RoleSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link RoleRepository}. */
@Component
@RequiredArgsConstructor
public class RoleRepositoryAdapter implements RoleRepository {

    private final RoleJpaRepository jpaRepository;
    private final RolePersistenceMapper mapper;

    @Override
    public Role save(Role role) {
        RoleEntity saved = jpaRepository.save(mapper.toEntity(role));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Role> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Role> findByName(String name) {
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
    public List<Role> list(RoleCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<RoleEntity> entities = jpaRepository.findAll(
                RoleSpecification.build(criteria), sort);
        return mapper.toDomainList(entities);
    }

    @Override
    public DataPage<Role> page(RoleCriteria criteria) {
        Page<RoleEntity> page = jpaRepository.findAll(
                RoleSpecification.build(criteria),
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
                    "Role is referenced and cannot be deleted: " + id);
        }
        jpaRepository.deleteById(id);
    }

    private DataPage<Role> toDataPage(Page<RoleEntity> page) {
        List<Role> content = mapper.toDomainList(page.getContent());
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}