package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.EmployeePeriodicAssignment;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeePeriodicAssignmentEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps between {@link EmployeePeriodicAssignment} and {@link EmployeePeriodicAssignmentEntity}. */
@Mapper(
        componentModel = "spring",
        uses = {
                CountryPersistenceMapper.class,
                DepartmentPersistenceMapper.class,
                RolePersistenceMapper.class
        }
)
public interface EmployeePeriodicAssignmentPersistenceMapper {

    /**
     * Maps a domain assignment to its entity. Foreign references are stored as UUIDs.
     *
     * @param assignment source domain object
     * @return mapped entity
     */
    @Mapping(target = "countryId", source = "country.id")
    @Mapping(target = "departmentId", source = "department.id")
    @Mapping(target = "roleId", source = "role.id")
    EmployeePeriodicAssignmentEntity toEntity(EmployeePeriodicAssignment assignment);

    /**
     * Maps an assignment entity to its domain object, resolving foreign references through the
     * country, department, and role mappers.
     *
     * @param entity     source entity
     * @param country    resolved country entity
     * @param department resolved department entity
     * @param role       resolved role entity
     * @return mapped domain object
     */
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "employeeId", source = "entity.employeeId")
    @Mapping(target = "startDate", source = "entity.startDate")
    @Mapping(target = "endDate", source = "entity.endDate")
    @Mapping(target = "country", source = "country")
    @Mapping(target = "department", source = "department")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "employmentType", source = "entity.employmentType")
    @Mapping(target = "inEffect", source = "entity.inEffect")
    @Mapping(target = "payrollFrequency", source = "entity.payrollFrequency")
    EmployeePeriodicAssignment toDomain(
            EmployeePeriodicAssignmentEntity entity,
            CountryEntity country,
            DepartmentEntity department,
            RoleEntity role
    );
}