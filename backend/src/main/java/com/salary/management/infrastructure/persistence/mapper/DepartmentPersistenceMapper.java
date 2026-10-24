package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.Department;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import org.mapstruct.Mapper;

import java.util.List;

/** Maps between {@link Department} and {@link DepartmentEntity}. */
@Mapper(componentModel = "spring")
public interface DepartmentPersistenceMapper {

    /**
     * Maps a domain department to its entity.
     *
     * @param department source domain object
     * @return mapped entity
     */
    DepartmentEntity toEntity(Department department);

    /**
     * Maps a department entity to its domain object.
     *
     * @param entity source entity
     * @return mapped domain object
     */
    Department toDomain(DepartmentEntity entity);

    /**
     * Maps a list of department entities to domain objects.
     *
     * @param entities source entities
     * @return mapped domain objects
     */
    List<Department> toDomainList(List<DepartmentEntity> entities);
}