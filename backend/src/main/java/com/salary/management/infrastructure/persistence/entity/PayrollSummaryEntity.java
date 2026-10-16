package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.embeddable.AmountsEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
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
 * <p>Unique per employee and payroll period. Amounts are stored inline. The identifier is
 * assigned by the domain.</p>
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

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    @Column(name = "payroll_period_id", nullable = false)
    private UUID payrollPeriodId;

    @Column(name = "employee_periodic_assignment_id", nullable = false)
    private UUID employeePeriodicAssignmentId;

    @Embedded
    private AmountsEmbeddable amounts;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayrollSummaryStatus status;
}