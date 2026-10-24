package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.UUID;

/**
 * Reference department within the organisation.
 */
@Getter
public final class Department {

    @NotNull
    private final UUID id;

    @NotBlank
    @Size(min = 2, max = 10)
    private final String code;

    @NotBlank
    @Size(min = 2, max = 100)
    private final String name;

    private Department(Builder builder) {
        this.id = builder.id;
        this.code = builder.code;
        this.name = builder.name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Department withName(String newName) {
        return toBuilder().name(newName).build();
    }

    private Builder toBuilder() {
        return new Builder().id(this.id).code(this.code).name(this.name);
    }

    /** Hand-written builder validating the composed {@code Department}. */
    public static final class Builder {

        private UUID id;
        private String code;
        private String name;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Department build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }
            Department department = new Department(this);
            DomainValidation.validate(department);
            return department;
        }
    }
}