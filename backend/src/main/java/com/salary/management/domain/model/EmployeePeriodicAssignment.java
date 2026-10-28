package com.salary.management.domain.model;

import com.salary.management.common.validation.DomainValidation;
import com.salary.management.common.validation.ValidationError;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final Country country;

    @NotNull
    private final Department department;

    @NotNull
    private final Role role;

    @NotNull
    private final EmploymentType employmentType;

    @NotNull
    private final PayrollPeriodFrequency payrollFrequency;

    @NotNull
    private final Boolean inEffect;

    private EmployeePeriodicAssignment(Builder builder) {
        this.id = builder.id;
        this.employeeId = builder.employeeId;
        this.startDate = builder.startDate;
        this.endDate = builder.endDate;
        this.country = builder.country;
        this.department = builder.department;
        this.role = builder.role;
        this.employmentType = builder.employmentType;
        this.payrollFrequency = builder.payrollFrequency;
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

    public EmployeePeriodicAssignment withCountry(Country newCountry) {
        return toBuilder().country(newCountry).build();
    }

    public EmployeePeriodicAssignment withDepartment(Department newDepartment) {
        return toBuilder().department(newDepartment).build();
    }

    public EmployeePeriodicAssignment withRole(Role newRole) {
        return toBuilder().role(newRole).build();
    }

    public EmployeePeriodicAssignment withPayrollFrequency(PayrollPeriodFrequency newFrequency) {
        return toBuilder().payrollFrequency(newFrequency).build();
    }

    public EmployeePeriodicAssignment withEmploymentType(EmploymentType employmentType) {
        return toBuilder().employmentType(employmentType).build();
    }

    private Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .employeeId(this.employeeId)
                .startDate(this.startDate)
                .endDate(this.endDate)
                .country(this.country)
                .department(this.department)
                .role(this.role)
                .employmentType(this.employmentType)
                .payrollFrequency(this.payrollFrequency)
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
        private Country country;
        private Department department;
        private Role role;
        private EmploymentType employmentType;
        private PayrollPeriodFrequency payrollFrequency;
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

        public Builder country(Country country) {
            this.country = country;
            return this;
        }

        public Builder department(Department department) {
            this.department = department;
            return this;
        }

        public Builder role(Role role) {
            this.role = role;
            return this;
        }

        public Builder employmentType(EmploymentType employmentType) {
            this.employmentType = employmentType;
            return this;
        }

        public Builder payrollFrequency(PayrollPeriodFrequency payrollFrequency) {
            this.payrollFrequency = payrollFrequency;
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