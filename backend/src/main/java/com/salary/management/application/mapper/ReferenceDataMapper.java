package com.salary.management.application.mapper;

import com.salary.management.application.dto.ReferenceDataResponse;
import com.salary.management.domain.model.Department;
import com.salary.management.domain.model.Role;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReferenceDataMapper {

    ReferenceDataResponse toResponse(Department department);

    ReferenceDataResponse toResponse(Role role);
}