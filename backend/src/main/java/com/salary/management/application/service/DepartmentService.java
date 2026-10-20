package com.salary.management.application.service;

import com.salary.management.application.dto.ReferenceDataResponse;
import com.salary.management.application.mapper.ReferenceDataMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.repository.DepartmentRepository;
import com.salary.management.domain.repository.criteria.DepartmentCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DepartmentService {

    private final DepartmentRepository repository;
    private final ReferenceDataMapper mapper;

    public DataPage<ReferenceDataResponse> search(DepartmentCriteria criteria) {
        DataPage<Department> domainPage = repository.page(criteria);
        return domainPage.map(mapper::toResponse);
    }
}