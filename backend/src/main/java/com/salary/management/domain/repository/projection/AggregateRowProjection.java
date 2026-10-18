package com.salary.management.domain.repository.projection;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;

import java.math.BigDecimal;

/**
 * Mutable projection for one row of the dynamic aggregate query.
 *
 * <p>Only the fields matching the requested group keys are populated; the rest are null.
 * Display values ({@link RefDto}) are joined from the reference tables. The same class is
 * reused for the statistics query, where only the group-key fields plus {@code direction}
 * and {@code amount} are populated.</p>
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

    /** Sums and count for the aggregate query. */
    public BigDecimal sumBase;
    public BigDecimal sumVariable;
    public BigDecimal sumAllowances;
    public BigDecimal sumEmployerTaxes;
    public BigDecimal sumTotal;
    public Long sumEmployeeCount;

    /** Statistics query extra columns. */
    public String direction;
    public BigDecimal amount;
}