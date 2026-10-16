package com.salary.management.infrastructure.persistence.entity;

import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import jakarta.persistence.Column;
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

import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for a payroll period.
 *
 * <p>The domain requires {@code startDate} strictly before {@code endDate}. The identifier is
 * assigned by the domain.</p>
 */
@Entity
@Table(
        name = "payroll_periods",
        uniqueConstraints = @UniqueConstraint(name = "uk_payroll_period_name", columnNames = "name")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollPeriodEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Display name; domain allows 2 to 100 characters. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private PayrollPeriodFrequency frequency;

    @Column(name = "closed", nullable = false)
    private Boolean closed;
}