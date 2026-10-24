package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.repository.CountryRepository;
import com.salary.management.domain.repository.criteria.CountryCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.mapper.CountryPersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.CountryJpaRepository;
import com.salary.management.infrastructure.persistence.specification.CountrySpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link CountryRepository}. */
@Component
@RequiredArgsConstructor
public class CountryRepositoryAdapter implements CountryRepository {

    private final CountryJpaRepository jpaRepository;
    private final CountryPersistenceMapper mapper;

    @Override
    public Country save(Country country) {
        CountryEntity saved = jpaRepository.save(mapper.toEntity(country));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Country> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Country> findByIsoCode(String isoCode) {
        return jpaRepository.findByIsoCode(isoCode).map(mapper::toDomain);
    }

    @Override
    public List<Country> list(CountryCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<CountryEntity> entities = jpaRepository.findAll(
                CountrySpecification.build(criteria), sort);
        return mapper.toDomainList(entities);
    }

    @Override
    public DataPage<Country> page(CountryCriteria criteria) {
        Page<CountryEntity> page = jpaRepository.findAll(
                CountrySpecification.build(criteria),
                CriteriaUtils.toPageable(criteria));
        return toDataPage(page);
    }

    @Override
    public boolean existsByIsoCode(String isoCode) {
        return jpaRepository.existsByIsoCode(isoCode);
    }

    @Override
    public void deleteById(UUID id) {
        if (!jpaRepository.existsById(id)) {
            return;
        }
        if (jpaRepository.isReferenced(id)) {
            throw new DataIntegrityViolationException(
                    "Country is referenced and cannot be deleted: " + id);
        }
        jpaRepository.deleteById(id);
    }

    private DataPage<Country> toDataPage(Page<CountryEntity> page) {
        List<Country> content = mapper.toDomainList(page.getContent());
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}