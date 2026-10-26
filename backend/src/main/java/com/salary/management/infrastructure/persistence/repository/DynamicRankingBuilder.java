package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.AmountUtils;
import com.salary.management.infrastructure.persistence.embeddable.AmountEmbeddable;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerPercentileEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamicRankingBuilder {

    private static final List<Integer> PERCENTILES =
            Arrays.asList(0, 10, 25, 50, 75, 90, 100);
    private static final int MIN_POPULATION_FOR_PERCENTILE = 10;

    /** Default number of labor days a bi-weekly check covers. */
    private static final int DEFAULT_DAYS_IN_BIWEEKLY = 14;

    private final EntityManager entityManager;

    public record RankingAndPercentileResult(
            List<PayrollPeriodEarnerRankingEntity> rankings,
            List<PayrollPeriodEarnerPercentileEntity> percentiles
    ) {}

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    public RankingAndPercentileResult rankAndComputePercentilesForPeriod(
            PayrollPeriodEntity period,
            String referenceCurrencyCode,
            GroupKey key,
            int limit) {

        if (period == null) {
            throw new IllegalArgumentException("period must not be null");
        }

        List<PayrollPeriodEarnerRankingEntity> rankings = new ArrayList<>();
        rankings.addAll(rank(period, referenceCurrencyCode,
                EarnerRankingDirection.TOP, key, limit));
        rankings.addAll(rank(period, referenceCurrencyCode,
                EarnerRankingDirection.BOTTOM, key, limit));

        List<PayrollPeriodEarnerPercentileEntity> percentiles =
                computePercentiles(period, referenceCurrencyCode, key);

        return new RankingAndPercentileResult(rankings, percentiles);
    }

    // ------------------------------------------------------------------
    // Rankings (top 5 + bottom 5)
    // ------------------------------------------------------------------

    private int getDaysInBiweekly(PayrollPeriodEntity period){
        return DEFAULT_DAYS_IN_BIWEEKLY;
    }

    private int getDaysInMonth(PayrollPeriodEntity period){
        return YearMonth.from(period.getStartDate()).lengthOfMonth();
    }

    private List<PayrollPeriodEarnerRankingEntity> rank(
            PayrollPeriodEntity period,
            String referenceCurrencyCode,
            EarnerRankingDirection direction,
            GroupKey key,
            int limit
           ) {
        int daysInMonth = getDaysInMonth(period);
        int daysInBiweekly = getDaysInBiweekly(period);
        String partitionBy      = partitionBy(key);
        String orderDirection   = direction == EarnerRankingDirection.TOP ? "DESC" : "ASC";
        String selectDimensions = selectDimensions(key);
        String selectRawDims    = selectRawDimensions(key);

        String referenceTotalExpr = """
                SUM(CASE
                      WHEN s.amounts.currency = :refCurrency THEN s.amounts.total
                      ELSE s.amounts.total * cr.referenceAmount / cr.rate
                    END)
                """.trim();

        String daysCoveredExpr = """
                SUM(CASE
                      WHEN p.frequency = 'MONTHLY'   THEN :daysInMonth
                      WHEN p.frequency = 'BI_WEEKLY' THEN :daysInBiweekly
                      ELSE 0
                    END)
                """.trim();

        String monthlyRateExpr = """
                CASE
                  WHEN %s = 0 THEN NULL
                  ELSE (%s / %s) * :daysInMonth
                END
                """.formatted(daysCoveredExpr, referenceTotalExpr, daysCoveredExpr).trim();

        String jpql = """
        SELECT %s
               r.employeeId         AS employeeId,
               r.periodId           AS periodId,
               r.originalTotal      AS originalTotal,
               r.originalCurrency   AS originalCurrency,
               r.referenceTotal     AS referenceTotal,
               r.referenceCurrency  AS referenceCurrency,
               r.daysCovered        AS daysCovered,
               r.monthlyRate        AS monthlyRate,
               r.rank               AS rank
        FROM (
            SELECT %s
                   s.employee.id                     AS employeeId,
                   s.payrollPeriod.id                AS periodId,
                   SUM(s.amounts.total)              AS originalTotal,
                   MAX(s.amounts.currency)           AS originalCurrency,
                   %s                                AS referenceTotal,
                   :refCurrency                      AS referenceCurrency,
                   %s                                AS daysCovered,
                   %s                                AS monthlyRate,
                   CAST(ROW_NUMBER() OVER (
                       %s
                       ORDER BY
                         %s %s,
                         s.employee.id ASC
                   ) AS INTEGER)                     AS rank
            FROM PayrollSummaryEntity s
            INNER JOIN PayrollPeriodEntity p ON s.payrollPeriod.id = p.id
            LEFT JOIN CurrencyRateEntity cr
              ON cr.currencyCode = s.amounts.currency
             AND cr.referenceCurrencyCode = :refCurrency
            WHERE s.payrollPeriod.startDate >= :startDate
              AND s.payrollPeriod.startDate < :endDate
              AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
              AND (s.amounts.currency = :refCurrency OR cr.rate IS NOT NULL)
            GROUP BY
                   %s
                   s.employee.id,
                   s.payrollPeriod.id
        ) r
        WHERE r.rank <= :limit
        """.formatted(
                selectDimensions,          // 1: outer SELECT dimension
                selectRawDims,             // 2: inner SELECT dimension
                referenceTotalExpr,        // 3: referenceTotal
                daysCoveredExpr,           // 4: daysCovered
                monthlyRateExpr,           // 5: monthlyRate
                partitionBy,               // 6: PARTITION BY
                monthlyRateExpr,           // 7: ORDER BY expression
                orderDirection,            // 8: ORDER BY direction (DESC/ASC)
                groupByDimensions(key));   // 9: GROUP BY dimension

        return entityManager.createQuery(jpql, Tuple.class)
                .setParameter("startDate", period.getStartDate())
                .setParameter("endDate", period.getEndDate())
                .setParameter("refCurrency", referenceCurrencyCode)
                .setParameter("daysInMonth", daysInMonth)
                .setParameter("daysInBiweekly", daysInBiweekly)
                .setParameter("limit", limit)
                .getResultList()
                .stream()
                .map(t -> toRankingEntity(t, direction, key))
                .toList();
    }

    // ------------------------------------------------------------------
    // Percentiles (7 rows per group)
    // ------------------------------------------------------------------

    private List<PayrollPeriodEarnerPercentileEntity> computePercentiles(
            PayrollPeriodEntity period,
            String referenceCurrencyCode,
            GroupKey key) {

        int daysInMonth = getDaysInMonth(period);
        int daysInBiweekly = getDaysInBiweekly(period);

        // ----------------------------------------------------------------
        // 1. Dimension fragments. Three shapes are needed:
        //    - dimSelect:      the CTE projection with AS aliases
        //    - innerDimExpr:   the raw column path, used inside the CTE
        //                      (where `s` IS in scope)
        //    - outerGroupBy:   the projected alias, used outside the CTE
        //                      (where `s` is NOT in scope)
        // ----------------------------------------------------------------
        String dimSelect = "";
        String innerDimExpr = "";
        String outerGroupBy = "";

        if (key == null) {
            dimSelect = """
                NULL AS countryId,
                NULL AS departmentId,
                NULL AS roleId,
                NULL AS employmentType
                """.trim();
            innerDimExpr = "";
            outerGroupBy = "";
        } else {
            switch (key) {
                case COUNTRY -> {
                    dimSelect = """
                        s.assignment_country_id AS countryId,
                        NULL AS departmentId,
                        NULL AS roleId,
                        NULL AS employmentType
                        """.trim();
                    innerDimExpr = "s.assignment_country_id";
                    outerGroupBy = "countryId";
                }
                case DEPARTMENT -> {
                    dimSelect = """
                        NULL AS countryId,
                        s.assignment_department_id AS departmentId,
                        NULL AS roleId,
                        NULL AS employmentType
                        """.trim();
                    innerDimExpr = "s.assignment_department_id";
                    outerGroupBy = "departmentId";
                }
                case ROLE -> {
                    dimSelect = """
                        NULL AS countryId,
                        NULL AS departmentId,
                        s.assignment_role_id AS roleId,
                        NULL AS employmentType
                        """.trim();
                    innerDimExpr = "s.assignment_role_id";
                    outerGroupBy = "roleId";
                }
                case EMPLOYMENT_TYPE -> {
                    dimSelect = """
                        NULL AS countryId,
                        NULL AS departmentId,
                        NULL AS roleId,
                        s.assignment_employment_type AS employmentType
                        """.trim();
                    innerDimExpr = "s.assignment_employment_type";
                    outerGroupBy = "employmentType";
                }
                case CURRENCY, ORIGIN_COUNTRY ->
                        throw new IllegalArgumentException("Unsupported: " + key);
            }
        }

        // ----------------------------------------------------------------
        // 2. Annualized expression.
        // ----------------------------------------------------------------
        String annualizedExpr = """
            CASE
              WHEN SUM(CASE
                         WHEN p.frequency = 'MONTHLY'   THEN :daysInMonth
                         WHEN p.frequency = 'BI_WEEKLY' THEN :daysInBiweekly
                         ELSE 0
                       END) = 0
              THEN NULL
              ELSE ((SUM(CASE
                           WHEN s.currency = :refCurrency THEN s.total
                           ELSE s.total * cr.reference_amount / cr.rate
                         END)
                     / SUM(CASE
                             WHEN p.frequency = 'MONTHLY'   THEN :daysInMonth
                             WHEN p.frequency = 'BI_WEEKLY' THEN :daysInBiweekly
                             ELSE 0
                           END))
                    * :daysInMonth) * 12
            END
            """.trim();

        // ----------------------------------------------------------------
        // 3. Portable greatest(1, x) as CASE expressions.
        // ----------------------------------------------------------------
        java.util.function.IntFunction<String> rankExpr = p -> """
            CASE
              WHEN CAST(CEIL(n * %d / 100.0) AS INTEGER) < 1
              THEN 1
              ELSE CAST(CEIL(n * %d / 100.0) AS INTEGER)
            END
            """.formatted(p, p).trim();

        String p0Expr   = rankExpr.apply(0);
        String p10Expr  = rankExpr.apply(10);
        String p25Expr  = rankExpr.apply(25);
        String p50Expr  = rankExpr.apply(50);
        String p75Expr  = rankExpr.apply(75);
        String p90Expr  = rankExpr.apply(90);
        String p100Expr = rankExpr.apply(100);

        // ----------------------------------------------------------------
        // 4. Build the SQL.
        //    - partitionClause uses innerDimExpr (inside CTE, `s` in scope)
        //    - innerGroupBy    uses innerDimExpr (inside CTE)
        //    - outerGroupBy    uses the projected alias (outside CTE, `s` gone)
        // ----------------------------------------------------------------
        String partitionClause = innerDimExpr.isEmpty()
                ? ""
                : "PARTITION BY " + innerDimExpr;
        String innerGroupBy = innerDimExpr.isEmpty()
                ? ""
                : innerDimExpr + ",";
        String outerGroupByClause = outerGroupBy.isEmpty()
                ? ""
                : "GROUP BY " + outerGroupBy;

        String sql = """
            WITH Ranked AS (
                SELECT %s,
                       s.employee_id                                     AS employeeId,
                       %s                                                AS annualized,
                       ROW_NUMBER() OVER (
                           %s
                           ORDER BY %s ASC, s.employee_id ASC
                       )                                                 AS rn,
                       COUNT(*) OVER (%s)                                AS n
                FROM payroll_summaries s
                INNER JOIN payroll_periods p ON s.payroll_period_id = p.id
                LEFT JOIN currency_rates cr
                  ON cr.currency_code = s.currency
                 AND cr.reference_currency_code = :refCurrency
                WHERE s.payroll_period_id = :periodId
                  AND s.status = 'VALIDATED'
                  AND (s.currency = :refCurrency OR cr.rate IS NOT NULL)
                GROUP BY %s s.employee_id
            )
            SELECT countryId,
                   departmentId,
                   roleId,
                   employmentType,
                   MAX(n) AS n,
                   SUM(CASE WHEN rn = (%s) THEN annualized END) AS p0,
                   SUM(CASE WHEN rn = (%s) THEN annualized END) AS p10,
                   SUM(CASE WHEN rn = (%s) THEN annualized END) AS p25,
                   SUM(CASE WHEN rn = (%s) THEN annualized END) AS p50,
                   SUM(CASE WHEN rn = (%s) THEN annualized END) AS p75,
                   SUM(CASE WHEN rn = (%s) THEN annualized END) AS p90,
                   SUM(CASE WHEN rn = (%s) THEN annualized END) AS p100
            FROM Ranked
            %s
            HAVING MAX(n) >= :minPopulation
            """.formatted(
                dimSelect,
                annualizedExpr,
                partitionClause,
                annualizedExpr,
                partitionClause,
                innerGroupBy,
                p0Expr, p10Expr, p25Expr, p50Expr, p75Expr, p90Expr, p100Expr,
                outerGroupByClause);

        // ----------------------------------------------------------------
        // 5. Execute.
        // ----------------------------------------------------------------
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql)
                .setParameter("periodId", period.getId().toString())
                .setParameter("refCurrency", referenceCurrencyCode)
                .setParameter("daysInMonth", daysInMonth)
                .setParameter("daysInBiweekly", daysInBiweekly)
                .setParameter("minPopulation", MIN_POPULATION_FOR_PERCENTILE)
                .getResultList();

        // ----------------------------------------------------------------
        // 6. Map each group row to up to 7 percentile entities.
        // ----------------------------------------------------------------
        int[] percentiles = {0, 10, 25, 50, 75, 90, 100};
        List<PayrollPeriodEarnerPercentileEntity> out = new ArrayList<>();

        for (Object[] row : rows) {
            String countryId      = (String)  row[0];
            String departmentId   = (String)  row[1];
            String roleId         = (String)  row[2];
            String employmentType = (String)  row[3];
            int    n              = ((Number) row[4]).intValue();

            for (int i = 0; i < percentiles.length; i++) {
                BigDecimal amount = toBigDecimal(row[5 + i]);
                if (amount == null) continue;

                out.add(PayrollPeriodEarnerPercentileEntity.builder()
                        .id(UUID.randomUUID())
                        .payrollPeriodId(period.getId())
                        .countryId(countryId == null ? null : UUID.fromString(countryId))
                        .departmentId(departmentId == null ? null : UUID.fromString(departmentId))
                        .roleId(roleId == null ? null : UUID.fromString(roleId))
                        .employmentType(employmentType)
                        .percentile(percentiles[i])
                        .referenceAmount(toScaled(amount))
                        .referenceCurrency(referenceCurrencyCode)
                        .employeeCount(n)
                        .build());
            }
        }
        return out;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }
    // ------------------------------------------------------------------
    // JPQL fragments
    // ------------------------------------------------------------------

    private String selectDimensions(GroupKey key) {
        if (key == null) return "";
        return switch (key) {
            case COUNTRY -> "r.countryId           AS countryId, ";
            case DEPARTMENT -> "r.departmentId        AS departmentId, ";
            case ROLE -> "r.roleId              AS roleId, ";
            case EMPLOYMENT_TYPE -> "r.employmentType      AS employmentType, ";
            case CURRENCY, ORIGIN_COUNTRY -> throw new IllegalArgumentException("Unsupported: " + key);
        };
    }

    private String selectRawDimensions(GroupKey key) {
        if (key == null) return "";
        return switch (key) {
            case COUNTRY -> "s.assignmentCountryId     AS countryId, ";
            case DEPARTMENT -> "s.assignmentDepartmentId  AS departmentId, ";
            case ROLE -> "s.assignmentRoleId         AS roleId, ";
            case EMPLOYMENT_TYPE -> "s.assignmentEmploymentType AS employmentType, ";
            case CURRENCY, ORIGIN_COUNTRY -> throw new IllegalArgumentException("Unsupported: " + key);
        };
    }

    private String selectDimensionColumns(GroupKey key) {
        return selectRawDimensions(key);
    }

    private String partitionBy(GroupKey key) {
        if (key == null) return "";
        return switch (key) {
            case COUNTRY -> "PARTITION BY s.assignmentCountryId";
            case DEPARTMENT -> "PARTITION BY s.assignmentDepartmentId";
            case ROLE -> "PARTITION BY s.assignmentRoleId";
            case EMPLOYMENT_TYPE -> "PARTITION BY s.assignmentEmploymentType";
            case CURRENCY, ORIGIN_COUNTRY -> throw new IllegalArgumentException("Unsupported: " + key);
        };
    }

    private String groupByDimensions(GroupKey key) {
        if (key == null) return "";
        return switch (key) {
            case COUNTRY -> "s.assignmentCountryId, ";
            case DEPARTMENT -> "s.assignmentDepartmentId, ";
            case ROLE -> "s.assignmentRoleId, ";
            case EMPLOYMENT_TYPE -> "s.assignmentEmploymentType, ";
            case CURRENCY, ORIGIN_COUNTRY -> throw new IllegalArgumentException("Unsupported: " + key);
        };
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private PayrollPeriodEarnerRankingEntity toRankingEntity(
            Tuple t, EarnerRankingDirection direction, GroupKey key) {
        PayrollPeriodEarnerRankingEntity e = new PayrollPeriodEarnerRankingEntity();
        e.setId(UUID.randomUUID());
        e.setPayrollPeriodId(t.get("periodId", UUID.class));
        e.setStatus(PayrollSummaryStatus.VALIDATED);
        e.setDirection(direction);
        e.setEmployeeId(t.get("employeeId", UUID.class));
        e.setRank(((Number) t.get("rank")).intValue());

        if (key == GroupKey.COUNTRY)         e.setCountryId(t.get("countryId", UUID.class));
        if (key == GroupKey.DEPARTMENT)      e.setDepartmentId(t.get("departmentId", UUID.class));
        if (key == GroupKey.ROLE)            e.setRoleId(t.get("roleId", UUID.class));
        if (key == GroupKey.EMPLOYMENT_TYPE) e.setEmploymentType(t.get("employmentType", EmploymentType.class));

        e.setDaysCovered(((Number) t.get("daysCovered")).intValue());

        e.setMonthlyRateAmount(AmountEmbeddable.builder()
                .amount(toScaled(t.get("monthlyRate", BigDecimal.class)))
                .currency(t.get("referenceCurrency", String.class))
                .build());

        e.setOriginalAmount(toAmount(
                t.get("originalTotal", BigDecimal.class),
                t.get("originalCurrency", String.class)));
        e.setReferenceAmount(toAmount(
                t.get("referenceTotal", BigDecimal.class),
                t.get("referenceCurrency", String.class)));
        return e;
    }

    private AmountEmbeddable toAmount(BigDecimal amount, String currency) {
        return AmountEmbeddable.builder()
                .amount(toScaled(amount))
                .currency(currency)
                .build();
    }

    private BigDecimal toScaled(BigDecimal v) {
        return v == null ? null
                : v.setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}