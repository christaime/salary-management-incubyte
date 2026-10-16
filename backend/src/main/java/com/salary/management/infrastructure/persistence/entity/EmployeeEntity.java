package com.salary.management.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * JPA entity for an employee.
 *
 * <p>{@code originCountryId} and {@code birthDate} are nullable. The identifier is assigned by
 * the domain, so no generation strategy is declared.</p>
 */
@Entity
@Table(
        name = "employees",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_matricule", columnNames = "matricule")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Business key, unique across employees. */
    @Column(name = "matricule", nullable = false, length = 100)
    private String matricule;

    /** Full name. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Email address; RFC 5321 maximum length. */
    @Column(name = "email", nullable = false, length = 254)
    private String email;

    /** Optional date of birth, stored as ISO-8601 text. */
    @Column(name = "birth_date")
    private LocalDate birthDate;

    /** Optional reference to the origin country. */
    @Column(name = "origin_country_id")
    private UUID originCountryId;
}