package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollSummaryStatus;
import com.salary.management.domain.model.values.AmountUtils;
import com.salary.management.infrastructure.persistence.embeddable.AmountEmbeddable;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamicRankingBuilder {

    private final EntityManager entityManager;

    /**
     * Ranks the VALIDATED summaries of a period, partitioned by one dimension (or globally
     * when {@code key} is {@code null}), and returns the top or bottom {@code limit}
     * employees of each partition.
     *
     * <p>The original amount is selected as two scalar columns, {@code total} and
     * {@code currency}, not as the whole {@code AmountsEmbeddable}. The ranking stores a
     * single amount, and selecting an embeddable from a subquery does not preserve its
     * column order across the subquery boundary.</p>
     *
     * <p>Both amounts are rounded to {@link AmountUtils#AMOUNT_SCALE} with
     * {@link RoundingMode#HALF_UP} before being written to the entity, matching every other
     * money value in the system.</p>
     *
     * @param payrollPeriodId       period to rank
     * @param referenceCurrencyCode currency to convert to for the ordering
     * @param direction             TOP (highest first) or BOTTOM (lowest first)
     * @param key                   partition dimension, or {@code null} for global
     * @param limit                 per-partition row cap
     * @return one row per ranked employee, with its rank within the partition
     */
    public List<PayrollPeriodEarnerRankingEntity> rank(
            UUID payrollPeriodId,
            String referenceCurrencyCode,
            EarnerRankingDirection direction,
            GroupKey key,
            int limit) {

        String partitionBy = partitionBy(key);
        String orderDirection = direction == EarnerRankingDirection.TOP ? "DESC" : "ASC";
        String selectDimensions = selectDimensions(key);

        String jpql = """
                SELECT %s
                       r.employeeId          AS employeeId,
                       r.periodId            AS periodId,
                       r.originalTotal       AS originalTotal,
                       r.originalCurrency    AS originalCurrency,
                       r.referenceTotal      AS referenceTotal,
                       r.referenceCurrency   AS referenceCurrency,
                       r.rank                AS rank
                FROM (
                    SELECT s.assignmentCountryId        AS countryId,
                           s.assignmentDepartmentId     AS departmentId,
                           s.assignmentRoleId           AS roleId,
                           s.assignmentEmploymentType   AS employmentType,
                           s.employee.id                AS employeeId,
                           s.payrollPeriod.id           AS periodId,
                           s.amounts.total              AS originalTotal,
                           s.amounts.currency           AS originalCurrency,
                           CASE
                             WHEN s.amounts.currency = :referenceCurrencyCode THEN s.amounts.total
                             ELSE s.amounts.total * cr.referenceAmount / cr.rate
                           END                          AS referenceTotal,
                           :referenceCurrencyCode       AS referenceCurrency,
                           CAST(ROW_NUMBER() OVER (
                               %s
                               ORDER BY
                                 CASE
                                   WHEN s.amounts.currency = :referenceCurrencyCode THEN s.amounts.total
                                   ELSE s.amounts.total * cr.referenceAmount / cr.rate
                                 END %s,
                                 s.employee.id ASC
                           ) AS INTEGER)                AS rank
                    FROM PayrollSummaryEntity s
                    LEFT JOIN CurrencyRateEntity cr
                      ON cr.currencyCode = s.amounts.currency
                     AND cr.referenceCurrencyCode = :referenceCurrencyCode
                    WHERE s.payrollPeriod.id = :payrollPeriodId
                      AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
                      AND (s.amounts.currency = :referenceCurrencyCode OR cr.rate IS NOT NULL)
                ) r
                WHERE r.rank <= :limit
                """.formatted(selectDimensions, partitionBy, orderDirection);

        return entityManager.createQuery(jpql, Tuple.class)
                .setParameter("payrollPeriodId", payrollPeriodId)
                .setParameter("referenceCurrencyCode", referenceCurrencyCode)
                .setParameter("limit", limit)
                .getResultList()
                .stream()
                .map(t -> toRankingEntity(t, payrollPeriodId, direction, key))
                .toList();
    }

    private String selectDimensions(GroupKey key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case COUNTRY -> "r.countryId           AS countryId, ";
            case DEPARTMENT -> "r.departmentId        AS departmentId, ";
            case ROLE -> "r.roleId              AS roleId, ";
            case EMPLOYMENT_TYPE -> "r.employmentType      AS employmentType, ";
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported ranking key: " + key);
        };
    }

    private String partitionBy(GroupKey key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case COUNTRY -> "PARTITION BY s.assignmentCountryId";
            case DEPARTMENT -> "PARTITION BY s.assignmentDepartmentId";
            case ROLE -> "PARTITION BY s.assignmentRoleId";
            case EMPLOYMENT_TYPE -> "PARTITION BY s.assignmentEmploymentType";
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported ranking key: " + key);
        };
    }

    private PayrollPeriodEarnerRankingEntity toRankingEntity(
            Tuple t, UUID payrollPeriodId, EarnerRankingDirection direction, GroupKey key) {
        PayrollPeriodEarnerRankingEntity entity = new PayrollPeriodEarnerRankingEntity();
        entity.setPayrollPeriodId(payrollPeriodId);
        entity.setStatus(PayrollSummaryStatus.VALIDATED);
        entity.setDirection(direction);
        entity.setEmployeeId(t.get("employeeId", UUID.class));
        entity.setRank(((Number) t.get("rank")).intValue());

        if (key == GroupKey.COUNTRY) {
            entity.setCountryId(t.get("countryId", UUID.class));
        }
        if (key == GroupKey.DEPARTMENT) {
            entity.setDepartmentId(t.get("departmentId", UUID.class));
        }
        if (key == GroupKey.ROLE) {
            entity.setRoleId(t.get("roleId", UUID.class));
        }
        if (key == GroupKey.EMPLOYMENT_TYPE) {
            entity.setEmploymentType(t.get("employmentType", EmploymentType.class));
        }

        BigDecimal originalTotal = t.get("originalTotal", BigDecimal.class);
        String originalCurrency = t.get("originalCurrency", String.class);
        entity.setOriginalAmount(toAmount(originalTotal, originalCurrency));

        BigDecimal referenceTotal = t.get("referenceTotal", BigDecimal.class);
        String referenceCurrency = t.get("referenceCurrency", String.class);
        entity.setReferenceAmount(toAmount(referenceTotal, referenceCurrency));
        return entity;
    }

    private AmountEmbeddable toAmount(BigDecimal amount, String currency) {
        BigDecimal scaled = amount == null
                ? null
                : amount.setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP);
        return AmountEmbeddable.builder()
                .amount(scaled)
                .currency(currency)
                .build();
    }
}