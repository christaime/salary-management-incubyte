package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for a time-bounded employee assignment.
 *
 * <p>Foreign references are stored as UUID columns only; no JPA relations. {@code endDate} is
 * nullable. The identifier is assigned by the domain.</p>
 */
@Entity
@Table(name = "employee_periodic_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeePeriodicAssignmentEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** Optional end date; when present the domain requires it to be after {@code startDate}. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "country_id", nullable = false)
    private UUID countryId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false)
    private EmploymentType employmentType;

    /** Cadence at which this assignment is paid. */
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private PayrollPeriodFrequency payrollFrequency;

    @Column(name = "in_effect", nullable = false)
    private Boolean inEffect;
}