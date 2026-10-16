package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.common.validation.DomainValidationException;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.enums.EmploymentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.util.*;

/**
 * Time-bounded assignment of an employee to a country, department and role.
 *
 * <p>Invariant: when an {@code endDate} is present, it must be strictly after
 * the {@code startDate}.</p>
 */
@Getter
public final class EmployeePeriodicAssignment {

    @NotNull
    private final UUID id;

    @NotNull
    private final UUID employeeId;

    @NotNull
    private final LocalDate startDate;

    private final LocalDate endDate;

    @NotNull
    private final UUID countryId;

    @NotNull
    private final UUID departmentId;

    @NotNull
    private final UUID roleId;

    @NotNull
    private final EmploymentType employmentType;

    @NotNull
    private final Boolean inEffect;

    private EmployeePeriodicAssignment(Builder builder) {
        this.id = builder.id;
        this.employeeId = builder.employeeId;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.countryId = builder.countryId;
        this.departmentId = builder.departmentId;
        this.roleId = builder.roleId;
        this.employmentType = builder.employmentType;
        this.inEffect = builder.inEffect;
    }

    public static Builder builder() {
        return new Builder();
    }

    public EmployeePeriodicAssignment withEndDate(LocalDate newEndDate) {
        return toBuilder().endDate(newEndDate).build();
    }

    public EmployeePeriodicAssignment withInEffect(boolean newInEffect) {
        return toBuilder().inEffect(newInEffect).build();
    }

    public EmployeePeriodicAssignment withRoleId(UUID newRoleId) {
        return toBuilder().roleId(newRoleId).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .employeeId(this.employeeId)
                .startDate(this.startDate)
                .endDate(this.endDate)
                .countryId(this.countryId)
                .departmentId(this.departmentId)
                .roleId(this.roleId)
                .employmentType(this.employmentType)
                .inEffect(this.inEffect);
    }

    /**
     * Hand-written builder validating the composed assignment, including the
     * {@code endDate} after {@code startDate} invariant.
     */
    public static final class Builder {

        private UUID id;
        private UUID employeeId;
        private LocalDate startDate;
        private LocalDate endDate;
        private UUID countryId;
        private UUID departmentId;
        private UUID roleId;
        private EmploymentType employmentType;
        private Boolean inEffect;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder employeeId(UUID employeeId) {
            this.employeeId = employeeId;
            return this;
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder countryId(UUID countryId) {
            this.countryId = countryId;
            return this;
        }

        public Builder departmentId(UUID departmentId) {
            this.departmentId = departmentId;
            return this;
        }

        public Builder roleId(UUID roleId) {
            this.roleId = roleId;
            return this;
        }

        public Builder employmentType(EmploymentType employmentType) {
            this.employmentType = employmentType;
            return this;
        }

        public Builder inEffect(Boolean inEffect) {
            this.inEffect = inEffect;
            return this;
        }

        public EmployeePeriodicAssignment build() {
            if (this.id == null) {
                this.id = UUID.randomUUID();
            }

            EmployeePeriodicAssignment assignment = new EmployeePeriodicAssignment(this);

            List<ValidationError> extraErrors = new ArrayList<>();
            if (endDate != null && startDate != null && !endDate.isAfter(startDate)) {
                extraErrors.add(new ValidationError(
                        "endDate",
                        "DateOrder",
                        "endDate must be after startDate",
                        Map.of()
                ));
            }

            DomainValidation.validate(assignment, extraErrors);
            return assignment;
        }
    }
}