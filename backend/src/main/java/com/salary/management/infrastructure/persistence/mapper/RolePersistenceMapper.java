package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.Role;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import org.mapstruct.Mapper;

import java.util.List;

/** Maps between {@link Role} and {@link RoleEntity}. */
@Mapper(componentModel = "spring")
public interface RolePersistenceMapper {

    /**
     * Maps a domain role to its entity.
     *
     * @param role source domain object
     * @return mapped entity
     */
    RoleEntity toEntity(Role role);

    /**
     * Maps a role entity to its domain object.
     *
     * @param entity source entity
     * @return mapped domain object
     */
    Role toDomain(RoleEntity entity);

    /**
     * Maps a list of role entities to domain objects.
     *
     * @param entities source entities
     * @return mapped domain objects
     */
    List<Role> toDomainList(List<RoleEntity> entities);
}