package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.enums.PayrollPeriodFrequency;
import com.salary.management.domain.model.values.AmountUtils;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DynamicStatisticsBuilder {

    private final EntityManager entityManager;
    private final CountryJpaRepository countryJpaRepository;
    private final DepartmentJpaRepository departmentJpaRepository;
    private final RoleJpaRepository roleJpaRepository;

    /**
     * Returns, per partition, the minimum and maximum reference amount among the ranked rows
     * of the given frequency, converting each ranking row's original amount to
     * {@code referenceCurrencyCode} against the current rates.
     *
     * <p>Each row of the result corresponds to one partition instance, not to one
     * (partition, currency) combination. The conversion to the reference currency happens
     * inside the aggregate, so {@code MIN} and {@code MAX} compare reference amounts across
     * currencies.</p>
     *
     * @param periodIds             periods to include
     * @param key                   partition dimension, or {@code null} for global
     * @param frequency             period frequency to consider
     * @param referenceCurrencyCode currency to convert to
     * @param countryIds            country filter, or {@code null}
     * @param departmentIds         department filter, or {@code null}
     * @param roleIds               role filter, or {@code null}
     * @param employmentTypes       employment type filter, or {@code null}
     * @return one row per partition, with the min and max reference amounts
     */
    public List<AggregateRowProjection> minMax(
            List<UUID> periodIds,
            GroupKey key,
            PayrollPeriodFrequency frequency,
            String referenceCurrencyCode,
            Collection<UUID> countryIds,
            Collection<UUID> departmentIds,
            Collection<UUID> roleIds,
            Collection<EmploymentType> employmentTypes) {

        String selectGroupColumn = selectGroupColumn(key);
        String groupByClause = groupByClause(key);
        String partitionFilter = partitionFilter(key);
        String filter = buildFilter(key, countryIds, departmentIds, roleIds, employmentTypes);

        String jpql = """
                SELECT %s
                       MIN(
                         CASE
                           WHEN r.originalAmount.currency = :referenceCurrencyCode
                             THEN r.originalAmount.amount
                           ELSE r.originalAmount.amount * cr.referenceAmount / cr.rate
                         END
                       )                                 AS minReferenceAmount,
                       MAX(
                         CASE
                           WHEN r.originalAmount.currency = :referenceCurrencyCode
                             THEN r.originalAmount.amount
                           ELSE r.originalAmount.amount * cr.referenceAmount / cr.rate
                         END
                       )                                 AS maxReferenceAmount
                FROM PayrollPeriodEarnerRankingEntity r
                JOIN PayrollPeriodEntity p ON p.id = r.payrollPeriodId
                LEFT JOIN CurrencyRateEntity cr
                  ON cr.currencyCode = r.originalAmount.currency
                 AND cr.referenceCurrencyCode = :referenceCurrencyCode
                WHERE r.payrollPeriodId IN :periodIds
                  AND r.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
                  AND p.frequency = :frequency
                  AND %s
                  %s
                  AND (r.originalAmount.currency = :referenceCurrencyCode
                       OR cr.rate IS NOT NULL)
                %s
                """.formatted(selectGroupColumn, partitionFilter, filter, groupByClause);

        TypedQuery<Tuple> query = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("periodIds", periodIds)
                .setParameter("frequency", frequency)
                .setParameter("referenceCurrencyCode", referenceCurrencyCode);
        if (key == GroupKey.COUNTRY && countryIds != null) {
            query.setParameter("countryIds", countryIds);
        }
        if (key == GroupKey.DEPARTMENT && departmentIds != null) {
            query.setParameter("departmentIds", departmentIds);
        }
        if (key == GroupKey.ROLE && roleIds != null) {
            query.setParameter("roleIds", roleIds);
        }
        if (key == GroupKey.EMPLOYMENT_TYPE && employmentTypes != null) {
            query.setParameter("employmentTypes", employmentTypes);
        }

        return query.getResultList().stream()
                .map(t -> toProjection(t, key))
                .filter(Objects::nonNull)
                .toList();
    }

    private String selectGroupColumn(GroupKey key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case COUNTRY -> "r.countryId AS countryId, ";
            case DEPARTMENT -> "r.departmentId AS departmentId, ";
            case ROLE -> "r.roleId AS roleId, ";
            case EMPLOYMENT_TYPE -> "r.employmentType AS employmentType, ";
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported statistics key: " + key);
        };
    }

    /**
     * Returns the complete {@code GROUP BY} clause, including the keyword, or the empty
     * string for the global partition where no grouping is required.
     */
    private String groupByClause(GroupKey key) {
        if (key == null) {
            return "";
        }
        return "GROUP BY " + switch (key) {
            case COUNTRY -> "r.countryId";
            case DEPARTMENT -> "r.departmentId";
            case ROLE -> "r.roleId";
            case EMPLOYMENT_TYPE -> "r.employmentType";
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported statistics key: " + key);
        };
    }

    private String partitionFilter(GroupKey key) {
        if (key == null) {
            return """
                    r.countryId IS NULL
                    AND r.departmentId IS NULL
                    AND r.roleId IS NULL
                    AND r.employmentType IS NULL
                    """;
        }
        return switch (key) {
            case COUNTRY -> """
                    r.countryId IS NOT NULL
                    AND r.departmentId IS NULL
                    AND r.roleId IS NULL
                    AND r.employmentType IS NULL
                    """;
            case DEPARTMENT -> """
                    r.countryId IS NULL
                    AND r.departmentId IS NOT NULL
                    AND r.roleId IS NULL
                    AND r.employmentType IS NULL
                    """;
            case ROLE -> """
                    r.countryId IS NULL
                    AND r.departmentId IS NULL
                    AND r.roleId IS NOT NULL
                    AND r.employmentType IS NULL
                    """;
            case EMPLOYMENT_TYPE -> """
                    r.countryId IS NULL
                    AND r.departmentId IS NULL
                    AND r.roleId IS NULL
                    AND r.employmentType IS NOT NULL
                    """;
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported statistics key: " + key);
        };
    }

    private String buildFilter(GroupKey key,
                               Collection<UUID> countryIds, Collection<UUID> departmentIds,
                               Collection<UUID> roleIds, Collection<EmploymentType> employmentTypes) {
        StringBuilder sb = new StringBuilder();
        if (key == GroupKey.COUNTRY && countryIds != null && !countryIds.isEmpty()) {
            sb.append(" AND r.countryId IN :countryIds ");
        }
        if (key == GroupKey.DEPARTMENT && departmentIds != null && !departmentIds.isEmpty()) {
            sb.append(" AND r.departmentId IN :departmentIds ");
        }
        if (key == GroupKey.ROLE && roleIds != null && !roleIds.isEmpty()) {
            sb.append(" AND r.roleId IN :roleIds ");
        }
        if (key == GroupKey.EMPLOYMENT_TYPE
                && employmentTypes != null && !employmentTypes.isEmpty()) {
            sb.append(" AND r.employmentType IN :employmentTypes ");
        }
        return sb.toString();
    }

    private AggregateRowProjection toProjection(Tuple t, GroupKey key) {
        AggregateRowProjection p = new AggregateRowProjection();
        if (key == GroupKey.COUNTRY) {
            UUID countryId = t.get("countryId", UUID.class);
            CountryEntity c = countryJpaRepository.findById(countryId).orElseThrow();
            p.country = new RefDto(c.getId(), c.getIsoCode(), c.getName());
        }
        if (key == GroupKey.DEPARTMENT) {
            UUID departmentId = t.get("departmentId", UUID.class);
            DepartmentEntity d = departmentJpaRepository.findById(departmentId).orElseThrow();
            p.department = new RefDto(d.getId(), d.getCode(), d.getName());
        }
        if (key == GroupKey.ROLE) {
            UUID roleId = t.get("roleId", UUID.class);
            RoleEntity r = roleJpaRepository.findById(roleId).orElseThrow();
            p.role = new RefDto(r.getId(), r.getCode(), r.getName());
        }
        if (key == GroupKey.EMPLOYMENT_TYPE) {
            p.employmentType = t.get("employmentType", EmploymentType.class);
        }
        BigDecimal min = t.get("minReferenceAmount", BigDecimal.class);
        BigDecimal max = t.get("maxReferenceAmount", BigDecimal.class);
        if (min == null && max == null) {
            return null;
        }
        p.minReferenceAmount = min == null ? null
                : min.setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP);
        p.maxReferenceAmount = max == null ? null
                : max.setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP);
        return p;
    }
}