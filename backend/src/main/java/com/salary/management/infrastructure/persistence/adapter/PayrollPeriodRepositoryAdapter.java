package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import com.salary.management.infrastructure.persistence.adapter.utils.CriteriaUtils;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import com.salary.management.infrastructure.persistence.mapper.PayrollPeriodPersistenceMapper;
import com.salary.management.infrastructure.persistence.repository.PayrollPeriodJpaRepository;
import com.salary.management.infrastructure.persistence.specification.PayrollPeriodSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence adapter implementing {@link PayrollPeriodRepository}. */
@Component
@RequiredArgsConstructor
public class PayrollPeriodRepositoryAdapter implements PayrollPeriodRepository {

    private final PayrollPeriodJpaRepository jpaRepository;
    private final PayrollPeriodPersistenceMapper mapper;

    @Override
    public PayrollPeriod save(PayrollPeriod payrollPeriod) {
        PayrollPeriodEntity saved = jpaRepository.save(mapper.toEntity(payrollPeriod));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<PayrollPeriod> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<PayrollPeriod> findByName(String name) {
        return jpaRepository.findByName(name).map(mapper::toDomain);
    }

    @Override
    public List<PayrollPeriod> list(PayrollPeriodCriteria criteria) {
        Sort sort = CriteriaUtils.toSpringSort(criteria);
        List<PayrollPeriodEntity> entities = jpaRepository.findAll(
                PayrollPeriodSpecification.build(criteria), sort);
        return entities.stream().map(mapper::toDomain).toList();
    }

    @Override
    public DataPage<PayrollPeriod> page(PayrollPeriodCriteria criteria) {
        Page<PayrollPeriodEntity> page = jpaRepository.findAll(
                PayrollPeriodSpecification.build(criteria),
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
                    "PayrollPeriod is referenced and cannot be deleted: " + id);
        }
        jpaRepository.deleteById(id);
    }

    private DataPage<PayrollPeriod> toDataPage(Page<PayrollPeriodEntity> page) {
        List<PayrollPeriod> content =
                page.getContent().stream().map(mapper::toDomain).toList();
        return new DataPage<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }

}