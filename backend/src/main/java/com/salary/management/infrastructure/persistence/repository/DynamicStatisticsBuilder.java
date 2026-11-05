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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class DynamicStatisticsBuilder {

    private final EntityManager entityManager;
    private final CountryJpaRepository countryJpaRepository;
    private final DepartmentJpaRepository departmentJpaRepository;
    private final RoleJpaRepository roleJpaRepository;

    public List<AggregateRowProjection> minMax(
            LocalDate startDate, LocalDate endDate,
            GroupKey key,
            PayrollPeriodFrequency frequency,
            String referenceCurrencyCode,
            Collection<UUID> countryIds,
            Collection<UUID> departmentIds,
            Collection<UUID> roleIds,
            Collection<EmploymentType> employmentTypes) {

        if (log.isDebugEnabled()) {
            log.debug("minMax: start — dateRangeStart={} dateRangeEnd={} key={} frequency={} referenceCurrency={} "
                            + "countries={} departments={} roles={} employmentTypes={}",
                    startDate, endDate, key, frequency, referenceCurrencyCode,
                    countOf(countryIds), countOf(departmentIds),
                    countOf(roleIds), countOf(employmentTypes));
        }

        String selectGroupColumn = selectGroupColumn(key);
        String groupByClause     = groupByClause(key);
        String filter            = filterForSummary(
                key, countryIds, departmentIds, roleIds, employmentTypes);

        // * 1.0 forces real division. Without it, SQLite does INTEGER / INTEGER
        // when cr.referenceAmount and cr.rate are both whole numbers (NUMERIC
        // affinity stores whole values as INTEGER).
        String jpql = """
                SELECT %s
                       MIN(
                         CASE
                           WHEN s.amounts.currency = :referenceCurrencyCode
                             THEN s.amounts.total
                           ELSE s.amounts.total * cr.referenceAmount * 1.0 / cr.rate
                         END
                       )                                 AS minReferenceAmount,
                       MAX(
                         CASE
                           WHEN s.amounts.currency = :referenceCurrencyCode
                             THEN s.amounts.total
                           ELSE s.amounts.total * cr.referenceAmount * 1.0 / cr.rate
                         END
                       )                                 AS maxReferenceAmount,
                       SUM(
                         CASE
                           WHEN s.amounts.currency = :referenceCurrencyCode
                             THEN s.amounts.total
                           ELSE ROUND(s.amounts.total * cr.referenceAmount * 1.0 / cr.rate, 2)
                         END
                       )                                 AS totalReferenceAmount,
                       COUNT(DISTINCT per.id)            AS periodCount,
                       COUNT(DISTINCT s.employee.id)     AS employeeCount
                FROM PayrollSummaryEntity s
                INNER JOIN PayrollPeriodEntity per ON per.id = s.payrollPeriod.id
                LEFT JOIN CurrencyRateEntity cr
                  ON cr.currencyCode = s.amounts.currency
                 AND cr.referenceCurrencyCode = :referenceCurrencyCode
                WHERE s.payrollPeriod.startDate >= :startDate AND s.payrollPeriod.startDate < :endDate
                  AND s.status = com.salary.management.domain.model.enums.PayrollSummaryStatus.VALIDATED
                  AND per.frequency = :frequency
                  AND (s.amounts.currency = :referenceCurrencyCode  OR cr.rate IS NOT NULL)
                  %s
                %s
                """.formatted(selectGroupColumn, filter, groupByClause);

        log.trace("minMax: JPQL:\n{}", jpql);

        TypedQuery<Tuple> query = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("frequency", frequency)
                .setParameter("referenceCurrencyCode", referenceCurrencyCode);
        if (countryIds != null && !countryIds.isEmpty()) {
            query.setParameter("countryIds", countryIds);
        }
        if (departmentIds != null && !departmentIds.isEmpty()) {
            query.setParameter("departmentIds", departmentIds);
        }
        if (roleIds != null && !roleIds.isEmpty()) {
            query.setParameter("roleIds", roleIds);
        }
        if (employmentTypes != null && !employmentTypes.isEmpty()) {
            query.setParameter("employmentTypes", employmentTypes);
        }

        List<Tuple> tuples;
        try {
            tuples = query.getResultList();
        } catch (RuntimeException ex) {
            log.error("minMax: query failed — key={} frequency={} referenceCurrency={} "
                            + "dateRangeStart={} dateRangeEnd={} countries={} departments={} roles={} employmentTypes={}",
                    key, frequency, referenceCurrencyCode,
                    startDate, endDate, countOf(countryIds), countOf(departmentIds),
                    countOf(roleIds), countOf(employmentTypes),
                    ex);
            throw ex;
        }

        int rawCount = tuples.size();
        List<AggregateRowProjection> projections = tuples.stream()
                .map(t -> toProjection(t, key, frequency))
                .filter(Objects::nonNull)
                .toList();

        if (rawCount != projections.size()) {
            log.debug("minMax: dropped {} of {} tuples (null min & max) — key={} frequency={}",
                    rawCount - projections.size(), rawCount, key, frequency);
        }
        log.debug("minMax: done — key={} frequency={} partitions={}",
                key, frequency, projections.size());

        return projections;
    }

    private String selectGroupColumn(GroupKey key) {
        if (key == null) {
            return "";
        }
        return switch (key) {
            case COUNTRY -> "s.assignmentCountryId AS countryId, ";
            case DEPARTMENT -> "s.assignmentDepartmentId AS departmentId, ";
            case ROLE -> "s.assignmentRoleId AS roleId, ";
            case EMPLOYMENT_TYPE -> "s.assignmentEmploymentType AS employmentType, ";
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported statistics key: " + key);
        };
    }

    private String groupByClause(GroupKey key) {
        if (key == null) {
            return "";
        }
        return "GROUP BY " + switch (key) {
            case COUNTRY -> "s.assignmentCountryId";
            case DEPARTMENT -> "s.assignmentDepartmentId";
            case ROLE -> "s.assignmentRoleId";
            case EMPLOYMENT_TYPE -> "s.assignmentEmploymentType";
            case CURRENCY, ORIGIN_COUNTRY ->
                    throw new IllegalArgumentException("Unsupported statistics key: " + key);
        };
    }

    private String filterForSummary(GroupKey key,
                                    Collection<UUID> countryIds,
                                    Collection<UUID> departmentIds,
                                    Collection<UUID> roleIds,
                                    Collection<EmploymentType> employmentTypes) {
        StringBuilder sb = new StringBuilder();
        if (countryIds != null && !countryIds.isEmpty()) {
            sb.append(" AND s.assignmentCountryId IN :countryIds ");
        }
        if (departmentIds != null && !departmentIds.isEmpty()) {
            sb.append(" AND s.assignmentDepartmentId IN :departmentIds ");
        }
        if (roleIds != null && !roleIds.isEmpty()) {
            sb.append(" AND s.assignmentRoleId IN :roleIds ");
        }
        if (employmentTypes != null && !employmentTypes.isEmpty()) {
            sb.append(" AND s.assignmentEmploymentType IN :employmentTypes ");
        }
        return sb.toString();
    }

    private AggregateRowProjection toProjection(Tuple t, GroupKey key,
                                                PayrollPeriodFrequency frequency) {
        AggregateRowProjection p = new AggregateRowProjection();
        if (key == GroupKey.COUNTRY) {
            UUID countryId = t.get("countryId", UUID.class);
            CountryEntity c = countryJpaRepository.findById(countryId).orElse(null);
            if (c == null) {
                log.warn("minMax toProjection: country {} referenced by summary row not found", countryId);
                throw new IllegalStateException("Country not found: " + countryId);
            }
            p.country = new RefDto(c.getId(), c.getIsoCode(), c.getName());
        }
        if (key == GroupKey.DEPARTMENT) {
            UUID departmentId = t.get("departmentId", UUID.class);
            DepartmentEntity d = departmentJpaRepository.findById(departmentId).orElse(null);
            if (d == null) {
                log.warn("minMax toProjection: department {} referenced by summary row not found", departmentId);
                throw new IllegalStateException("Department not found: " + departmentId);
            }
            p.department = new RefDto(d.getId(), d.getCode(), d.getName());
        }
        if (key == GroupKey.ROLE) {
            UUID roleId = t.get("roleId", UUID.class);
            RoleEntity r = roleJpaRepository.findById(roleId).orElse(null);
            if (r == null) {
                log.warn("minMax toProjection: role {} referenced by summary row not found", roleId);
                throw new IllegalStateException("Role not found: " + roleId);
            }
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
        Long employeeCount = t.get("employeeCount", Long.class);
        Long periodCount = t.get("periodCount", Long.class);
        BigDecimal totalReferenceAmount = t.get("totalReferenceAmount", BigDecimal.class);
        if (frequency.equals(PayrollPeriodFrequency.MONTHLY)) {
            p.monthlyTotal = totalReferenceAmount;
            p.monthlyPeriodCount = periodCount;
            p.monthlyEmployeeCount = employeeCount;
        } else {
            p.biWeeklyTotal = totalReferenceAmount;
            p.biWeeklyPeriodCount = periodCount;
            p.biWeeklyEmployeeCount = employeeCount;
        }

        return p;
    }

    private static int countOf(Collection<?> c) {
        return c == null ? 0 : c.size();
    }
}