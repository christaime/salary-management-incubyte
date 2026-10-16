package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.domain.repository.CurrencyRateRepository;
import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.CurrencyRateEntity;
import com.salary.management.infrastructure.persistence.mapper.CurrencyRatePersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.CurrencyRateJpaRepository;
import com.salary.management.infrastructure.persistence.specification.CurrencyRateSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link CurrencyRateRepository}. */
@Component
@RequiredArgsConstructor
public class CurrencyRateRepositoryAdapter implements CurrencyRateRepository {

    private final CurrencyRateJpaRepository jpaRepository;
    private final CurrencyRatePersistenceMapper mapper;

    @Override
    public CurrencyRate save(CurrencyRate currencyRate) {
        CurrencyRateEntity saved = jpaRepository.save(mapper.toEntity(currencyRate));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<CurrencyRate> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<CurrencyRate> findByCurrencyCodeAndReferenceCurrencyCode(
            String currencyCode, String referenceCurrencyCode) {
        return jpaRepository
                .findByCurrencyCodeAndReferenceCurrencyCode(currencyCode, referenceCurrencyCode)
                .map(mapper::toDomain);
    }

    @Override
    public List<CurrencyRate> list(CurrencyRateCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<CurrencyRateEntity> entities = jpaRepository.findAll(
                CurrencyRateSpecification.build(criteria), sort);
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public DataPage<CurrencyRate> page(CurrencyRateCriteria criteria) {
        Page<CurrencyRateEntity> page = jpaRepository.findAll(
                CurrencyRateSpecification.build(criteria),
                CriteriaUtils.toPageable(criteria));
        return toDataPage(page);
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.deleteById(id);
    }

    private DataPage<CurrencyRate> toDataPage(Page<CurrencyRateEntity> page) {
        List<CurrencyRate> content =
                page.getContent().stream().map(mapper::toDomain).toList();
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}