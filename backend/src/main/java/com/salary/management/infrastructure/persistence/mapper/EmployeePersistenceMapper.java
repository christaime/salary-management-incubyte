package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.Country;
import com.salary.management.domain.model.Employee;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.EmployeeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps between {@link Employee} and {@link EmployeeEntity}. */
@Mapper(
        componentModel = "spring",
        uses = {
                CountryPersistenceMapper.class
        }
)
public interface EmployeePersistenceMapper {

    /**
     * Maps a domain employee to its entity. The optional {@code originCountry} is stored as
     * {@code originCountryId}; a {@code null} origin country maps to a {@code null} id.
     *
     * @param employee source domain object
     * @return mapped entity
     */
    @Mapping(target = "originCountryId", source = "originCountry.id")
    EmployeeEntity toEntity(Employee employee);

    /**
     * Maps an employee entity to its domain object, given the already-resolved origin country.
     *
     * @param entity        source entity
     * @param originCountry resolved origin country entity, or {@code null}
     * @return mapped domain object
     */
    @Mapping(target = "id", source = "entity.id")
    @Mapping(target = "matricule", source = "entity.matricule")
    @Mapping(target = "name", source = "entity.name")
    @Mapping(target = "email", source = "entity.email")
    @Mapping(target = "birthDate", source = "entity.birthDate")
    @Mapping(target = "originCountry", source = "originCountry")
    Employee toDomain(EmployeeEntity entity, CountryEntity originCountry);
}