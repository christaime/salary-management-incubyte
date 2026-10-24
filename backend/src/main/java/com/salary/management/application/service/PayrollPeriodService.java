package com.salary.management.application.service;

import com.salary.management.application.dto.PayrollPeriodResponse;
import com.salary.management.application.mapper.PayrollPeriodApiMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.PayrollPeriod;
import com.salary.management.domain.repository.PayrollPeriodRepository;
import com.salary.management.domain.repository.criteria.PayrollPeriodCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollPeriodService {

    private final PayrollPeriodRepository repository;
    private final PayrollPeriodApiMapper mapper;

    public DataPage<PayrollPeriodResponse> search(PayrollPeriodCriteria criteria) {
        DataPage<PayrollPeriod> domainPage = repository.page(criteria);
        return domainPage.map(mapper::toResponse);
    }
}