package com.salary.management.application.service;


import com.salary.management.application.dto.CountryResponse;
import com.salary.management.application.mapper.CountryApiMapper;
import com.salary.management.common.pagination.DataPage;
import com.salary.management.domain.model.Country;
import com.salary.management.domain.repository.CountryRepository;
import com.salary.management.domain.repository.criteria.CountryCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CountryService {

    private final CountryRepository repository;
    private final CountryApiMapper mapper;

    public DataPage<CountryResponse> search(CountryCriteria criteria) {
        DataPage<Country> domainPage = repository.page(criteria);
        return domainPage.map(mapper::toResponse);
    }
}