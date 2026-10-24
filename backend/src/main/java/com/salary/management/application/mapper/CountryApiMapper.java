package com.salary.management.application.mapper;


import com.salary.management.application.dto.CountryResponse;
import com.salary.management.domain.model.Country;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CountryApiMapper {

    @Mapping(target = "id", source = "id")
    @Mapping(target = "code", source = "isoCode")
    CountryResponse toResponse(Country country);

}