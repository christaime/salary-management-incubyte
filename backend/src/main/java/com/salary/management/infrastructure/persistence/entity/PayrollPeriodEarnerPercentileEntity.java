package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "payroll_period_earner_percentiles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_earner_percentile_group",
                columnNames = {"payroll_period_id", "country_id", "department_id",
                        "role_id", "employment_type", "percentile"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriodEarnerPercentileEntity {

    @Id
    private UUID id;

    @Column(name = "payroll_period_id", nullable = false)
    private UUID payrollPeriodId;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "role_id")
    private UUID roleId;

    @Column(name = "employment_type")
    private String employmentType;

    @Column(name = "percentile", nullable = false)
    private Integer percentile;

    /** Annualized amount in reference currency. */
    @Column(name = "reference_amount", nullable = false)
    private BigDecimal referenceAmount;

    @Column(name = "reference_currency", nullable = false)
    private String referenceCurrency;

    /** Group size (n) at snapshot time. Same value on all 7 percentile rows of a group. */
    @Column(name = "employee_count", nullable = false)
    private Integer employeeCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PayrollSummaryStatus status;
}