package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.Country;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import org.mapstruct.Mapper;

import java.util.List;

/** Maps between {@link Country} and {@link CountryEntity}. */
@Mapper(componentModel = "spring")
public interface CountryPersistenceMapper {

    /**
     * Maps a domain country to its entity.
     *
     * @param country source domain object
     * @return mapped entity
     */
    CountryEntity toEntity(Country country);

    /**
     * Maps a country entity to its domain object.
     *
     * @param entity source entity
     * @return mapped domain object
     */
    Country toDomain(CountryEntity entity);

    /**
     * Maps a list of country entities to domain objects.
     *
     * @param entities source entities
     * @return mapped domain objects
     */
    List<Country> toDomainList(List<CountryEntity> entities);
}