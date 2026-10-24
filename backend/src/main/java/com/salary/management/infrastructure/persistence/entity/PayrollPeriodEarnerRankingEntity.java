package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.embeddable.AmountEmbeddable;
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
 * JPA entity for one entry in the top or bottom earner ranking of a payroll period,
 * scoped to a (country, department, role, employment type) combination.
 *
 * <p>Holds two {@link AmountEmbeddable} instances: the employee's total in the assignment
 * currency ({@code original_*}) and the same total converted to the reference currency
 * ({@code reference_*}). Column names are overridden so the two do not collide. The
 * identifier is assigned by the domain.</p>
 */
@Entity
@Table(
        name = "payroll_period_earner_rankings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_period_earner_ranking_group",
                columnNames = {
                        "payroll_period_id", "country_id", "department_id", "role_id",
                        "employment_type", "direction", "rank"
                }
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriodEarnerRankingEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payroll_period_id", nullable = false)
    private UUID payrollPeriodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayrollSummaryStatus status;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "role_id")
    private UUID roleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private EarnerRankingDirection direction;

    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;

    /** Employee total in the assignment currency. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "original_amount", nullable = false)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "original_currency", nullable = false, length = 3))
    })
    private AmountEmbeddable originalAmount;

    /** Employee total converted to the reference currency. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",
                    column = @Column(name = "reference_amount", nullable = false)),
            @AttributeOverride(name = "currency",
                    column = @Column(name = "reference_currency", nullable = false, length = 3))
    })
    private AmountEmbeddable referenceAmount;
}