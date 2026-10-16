package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

/**
 * Reference country. Carries the ISO code, display name and the currency
 * used for payroll in that country.
 */
@Getter
public final class Country {

    @NotNull
    private final UUID id;

    @NotBlank
    @Size(min = 2, max = 3)
    private final String isoCode;

    @NotBlank
    @Size(min = 2, max = 100)
    private final String name;

    @NotBlank
    @Size(min = 3, max = 3)
    private final String currencyCode;

    private Country(Builder builder) {
        this.id = builder.id;
        this.isoCode = builder.isoCode;
        this.name = builder.name;
        this.currencyCode = builder.currencyCode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Country withIsoCode(String newIsoCode) {
        return toBuilder().isoCode(newIsoCode).build();
    }

    public Country withName(String newName) {
        return toBuilder().name(newName).build();
    }

    public Country withCurrencyCode(String newCurrencyCode) {
        return toBuilder().currencyCode(newCurrencyCode).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .isoCode(this.isoCode)
                .name(this.name)
                .currencyCode(this.currencyCode);
    }

    /** Hand-written builder validating the composed {@code Country}. */
    public static final class Builder {

        private UUID id;
        private String isoCode;
        private String name;
        private String currencyCode;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder isoCode(String isoCode) {
            this.isoCode = isoCode;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder currencyCode(String currencyCode) {
            this.currencyCode = currencyCode;
            return this;
        }

        public Country build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            Country country = new Country(this);
            DomainValidation.validate(country);
            return country;
        }
    }
}