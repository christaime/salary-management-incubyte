package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.embeddable.AmountsEmbeddable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
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
 * JPA entity for an aggregated payroll row per period and
 * (country, department, role, employment type).
 *
 * <p>Holds two {@link AmountsEmbeddable} instances, one in the original currency and one in
 * the reference currency; column names are overridden so the two do not collide. The identifier
 * is assigned by the domain.</p>
 */
@Entity
@Table(
        name = "payroll_period_aggregates",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_period_aggregate_group",
                columnNames = {
                        "payroll_period_id", "country_id", "department_id", "role_id", "employment_type"
                }
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriodAggregateEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payroll_period_id", nullable = false)
    private UUID payrollPeriodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayrollSummaryStatus status;

    @Column(name = "country_id", nullable = false)
    private UUID countryId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false)
    private EmploymentType employmentType;

    /** Amounts in the original currency of the assignment. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "base", column = @Column(name = "original_base", nullable = false)),
            @AttributeOverride(name = "variable", column = @Column(name = "original_variable", nullable = false)),
            @AttributeOverride(name = "allowances", column = @Column(name = "original_allowances", nullable = false)),
            @AttributeOverride(name = "employerTaxes",
                    column = @Column(name = "original_employer_taxes", nullable = false)),
            @AttributeOverride(name = "total", column = @Column(name = "original_total", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "original_currency", nullable = false, length = 3))
    })
    private AmountsEmbeddable originalAmounts;

    /** Amounts converted to the reference currency. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "base", column = @Column(name = "reference_base", nullable = false)),
            @AttributeOverride(name = "variable", column = @Column(name = "reference_variable", nullable = false)),
            @AttributeOverride(name = "allowances", column = @Column(name = "reference_allowances", nullable = false)),
            @AttributeOverride(name = "employerTaxes",
                    column = @Column(name = "reference_employer_taxes", nullable = false)),
            @AttributeOverride(name = "total", column = @Column(name = "reference_total", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "reference_currency", nullable = false, length = 3))
    })
    private AmountsEmbeddable referenceAmounts;

    @Column(name = "employee_count", nullable = false)
    private Integer employeeCount;
}