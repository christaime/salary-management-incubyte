package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.infrastructure.persistence.embeddable.AmountEmbeddable;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "payroll_period_earner_rankings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payroll_period_earner_ranking_group",
                columnNames = {"payroll_period_id", "country_id", "department_id",
                        "role_id", "employment_type", "direction", "rank"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriodEarnerRankingEntity {

    @Id
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

    /** Number of labor days the raw sum covers (14, 28, 31, ...). */
    @Column(name = "days_covered")
    private Integer daysCovered;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",   column = @Column(name = "original_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "original_currency"))
    })
    private AmountEmbeddable originalAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",   column = @Column(name = "reference_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "reference_currency"))
    })
    private AmountEmbeddable referenceAmount;

    /**
     * Ranking key. Monthly rate = (referenceAmount / daysCovered) × daysInMonth.
     * Stored as an embeddable so amount + currency travel together.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount",   column = @Column(name = "monthly_rate_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "monthly_rate_currency"))
    })
    private AmountEmbeddable monthlyRateAmount;
}