package com.salary.management.domain.repository.projection;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Mutable projection for one row of the report queries.
 *
 * <p>For the aggregate query, the {@code sum*} fields and {@code sumEmployeeCount} are
 * populated, and the group-key fields ({@code country}, {@code department}, {@code role},
 * {@code employmentType}, {@code currency}) are filled only for the keys the client asked
 * for. For the statistics query, the group-key fields are populated the same way and
 * {@code minReferenceAmount} and {@code maxReferenceAmount} carry the per-group minimum
 * and maximum. Fields that the current query did not populate are left {@code null}.</p>
 */
public class AggregateRowProjection {

    /** COUNTRY group key value: code and name. */
    public RefDto country;

    /** DEPARTMENT group key value: code (id) and name. */
    public RefDto department;

    /** ROLE group key value: code (id) and name. */
    public RefDto role;

    /** EMPLOYMENT_TYPE group key value. */
    public EmploymentType employmentType;

    /** CURRENCY group key value. */
    public String currency;

    /** Raw dimension ids, available even when a display field is not requested. */
    public UUID countryId;
    public UUID departmentId;
    public UUID roleId;

    /** Aggregate query results. */
    public BigDecimal sumBase;
    public BigDecimal sumVariable;
    public BigDecimal sumAllowances;
    public BigDecimal sumEmployerTaxes;
    public BigDecimal sumTotal;
    public Long sumEmployeeCount;

    /** Statistics query results. */
    public BigDecimal minReferenceAmount;
    public BigDecimal maxReferenceAmount;
}