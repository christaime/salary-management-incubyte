package com.salary.management.application.service;

import com.salary.management.application.dto.ReferenceDataResponse;
import com.salary.management.application.mapper.ReferenceDataMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Role;
import com.salary.management.domain.repository.RoleRepository;
import com.salary.management.domain.repository.criteria.RoleCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository repository;
    private final ReferenceDataMapper mapper;

    public DataPage<ReferenceDataResponse> search(RoleCriteria criteria) {
        DataPage<Role> domainPage = repository.page(criteria);
        return domainPage.map(mapper::toResponse);
    }
}