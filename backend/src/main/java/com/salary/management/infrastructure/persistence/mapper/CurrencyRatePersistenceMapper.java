package com.salary.management.infrastructure.persistence.mapper;

import com.salary.management.domain.model.CurrencyRate;
import com.salary.management.infrastructure.persistence.entity.CurrencyRateEntity;
import org.mapstruct.Mapper;

/** Maps between {@link CurrencyRate} and {@link CurrencyRateEntity}. */
@Mapper(componentModel = "spring")
public interface CurrencyRatePersistenceMapper {

    /**
     * Maps a domain currency rate to its entity.
     *
     * @param currencyRate source domain object
     * @return mapped entity
     */
    CurrencyRateEntity toEntity(CurrencyRate currencyRate);

    /**
     * Maps a currency rate entity to its domain object.
     *
     * @param entity source entity
     * @return mapped domain object
     */
    CurrencyRate toDomain(CurrencyRateEntity entity);
}