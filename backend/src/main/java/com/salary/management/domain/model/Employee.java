package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.common.validation.ValidationError;
import jakarta.validation.constraints.*;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Employee aggregate root. Identity is by {@code id}; {@code matricule} is a
 * business key and is expected to be unique in persistence.
 *
 * <p>{@code originCountry} is optional.</p>
 */
@Getter
public final class Employee {

    @NotNull
    private final UUID id;

    @NotBlank
    @Size(max = 100)
    private final String matricule;

    @NotBlank
    @Size(max = 100)
    private final String name;

    @NotBlank
    @Email
    @Size(max = 254)
    private final String email;

    @Past
    private final LocalDate birthDate;

    private final Country originCountry;

    private Employee(Builder builder) {
        this.id = builder.id;
        this.matricule = builder.matricule;
        this.name = builder.name;
        this.email = builder.email;
        this.birthDate = builder.birthDate;
        this.originCountry = builder.originCountry;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Employee withName(String newName) {
        return toBuilder().name(newName).build();
    }

    public Employee withEmail(String newEmail) {
        return toBuilder().email(newEmail).build();
    }

    public Employee withOriginCountry(Country newOriginCountry) {
        return toBuilder().originCountry(newOriginCountry).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .matricule(this.matricule)
                .name(this.name)
                .email(this.email)
                .birthDate(this.birthDate)
                .originCountry(this.originCountry);
    }

    /** Hand-written builder validating the composed {@code Employee}. */
    public static final class Builder {

        private UUID id;
        private String matricule;
        private String name;
        private String email;
        private LocalDate birthDate;
        private Country originCountry;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder matricule(String matricule) {
            this.matricule = matricule;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder birthDate(LocalDate birthDate) {
            this.birthDate = birthDate;
            return this;
        }

        public Builder originCountry(Country originCountry) {
            this.originCountry = originCountry;
            return this;
        }

        public Employee build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            List<ValidationError> extraErrors = new ArrayList<>();
            if (birthDate != null && birthDate.atStartOfDay().toLocalDate().isAfter(LocalDate.now())) {
                extraErrors.add(new ValidationError(
                        "birthDate",
                        "DateOrder",
                        "birthDate must be in the past",
                        Map.of()
                ));
            }
            Employee employee = new Employee(this);
            DomainValidation.validate(employee, extraErrors);
            return employee;
        }
    }
}