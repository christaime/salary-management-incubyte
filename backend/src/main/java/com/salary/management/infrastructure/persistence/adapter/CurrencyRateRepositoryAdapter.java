package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.domain.repository.CurrencyRateRepository;
import com.salary.management.domain.repository.criteria.CurrencyRateCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.CurrencyRateEntity;
import com.salary.management.infrastructure.persistence.mapper.CurrencyRatePersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.CurrencyRateJpaRepository;
import com.salary.management.infrastructure.persistence.specification.CurrencyRateSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Persistence adapter implementing {@link CurrencyRateRepository}.
 *
 * <p>{@link #saveAll(List)} is replace-all: it deletes every stored row and inserts the
 * batch atomically. The single-reference invariant is checked on the batch itself; the
 * stored reference is deleted before the new rows land, so there is nothing to compare
 * against. Direct SQL writers are covered separately by a database trigger on
 * {@code currency_rates}.</p>
 */
@Component
@RequiredArgsConstructor
public class CurrencyRateRepositoryAdapter implements CurrencyRateRepository {

    private final CurrencyRateJpaRepository jpaRepository;
    private final CurrencyRatePersistenceMapper mapper;

    @Override
    @Transactional
    public List<CurrencyRate> saveAll(List<CurrencyRate> currencyRates) {
        if (currencyRates.isEmpty()) {
            throw new DataIntegrityViolationException(
                    "currency_rates saveAll requires at least one row; use deleteAll to clear");
        }
        enforceRateInvariants(currencyRates);
        jpaRepository.deleteAllInBatch();
        List<CurrencyRateEntity> entities = currencyRates.stream()
                .map(mapper::toEntity)
                .toList();
        return jpaRepository.saveAll(entities).stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAll() {
        jpaRepository.deleteAll();
    }

    @Override
    public String getReferenceCurrencyCode() {
        return jpaRepository.getReferenceCurrencyCode();
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

    /**
     * Enforces the single-reference invariant on the batch alone. The stored reference is
     * irrelevant under replace-all semantics: it will be deleted before the new rows land.
     */
    private void enforceRateInvariants(List<CurrencyRate> rates) {
        Set<String> references = rates.stream()
                .map(CurrencyRate::getReferenceCurrencyCode)
                .collect(Collectors.toSet());
        if (references.size() > 1) {
            throw new DataIntegrityViolationException(
                    "currency_rates batch mixes reference currencies: " + references);
        }
        for (CurrencyRate rate : rates) {
            if (rate.getCurrencyCode().equals(rate.getReferenceCurrencyCode())) {
                throw new DataIntegrityViolationException(
                        "currency_rates row must not pair a currency with itself: "
                                + rate.getCurrencyCode());
            }
        }
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