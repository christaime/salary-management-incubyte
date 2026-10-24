package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.embeddable.AmountsEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * JPA entity for a per-employee payroll summary.
 *
 * <p>Unique per employee and payroll period. The employee and payroll period are mapped as
 * eager {@code @ManyToOne} associations so the summary can be filtered and sorted on their
 * columns. The assignment is denormalized into {@code employeePeriodicAssignmentId} plus its
 * country, department, role, and employment type, so filtering by assignment attributes does
 * not require a join. Amounts are stored inline. The identifier is assigned by the domain.</p>
 */
@Entity
@Table(
        name = "payroll_summaries",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_summary_employee_period",
                columnNames = {"employee_id", "payroll_period_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollSummaryEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private EmployeeEntity employee;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "payroll_period_id", nullable = false)
    private PayrollPeriodEntity payrollPeriod;

    @Column(name = "employee_periodic_assignment_id", nullable = false)
    private UUID employeePeriodicAssignmentId;

    @Column(name = "assignment_country_id", nullable = false)
    private UUID assignmentCountryId;

    @Column(name = "assignment_department_id", nullable = false)
    private UUID assignmentDepartmentId;

    @Column(name = "assignment_role_id", nullable = false)
    private UUID assignmentRoleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_employment_type", nullable = false)
    private EmploymentType assignmentEmploymentType;

    @Embedded
    private AmountsEmbeddable amounts;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayrollSummaryStatus status;
}