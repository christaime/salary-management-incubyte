package com.salary.management.infrastructure.persistence.adapter;

import com.salary.management.application.dto.RefDto;
import com.salary.management.domain.model.enums.EarnerRankingDirection;
import com.salary.management.domain.model.enums.EmploymentType;
import com.salary.management.domain.model.enums.GroupKey;
import com.salary.management.domain.model.values.AmountUtils;
import com.salary.management.domain.repository.ReportDataRepository;
import com.salary.management.domain.repository.criteria.ReportEffectiveFilters;
import com.salary.management.domain.repository.projection.AggregateRowProjection;
import com.salary.management.infrastructure.persistence.entity.CountryEntity;
import com.salary.management.infrastructure.persistence.entity.DepartmentEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodAggregateEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import com.salary.management.infrastructure.persistence.entity.RoleEntity;
import com.salary.management.infrastructure.persistence.projection.StatisticsRow;
import com.salary.management.infrastructure.persistence.repository.PayrollPeriodEarnerRankingJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Selection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** Persistence adapter implementing {@link ReportDataRepository}. */
@Component
@RequiredArgsConstructor
public class ReportDataRepositoryAdapter implements ReportDataRepository {

    private final EntityManager entityManager;
    private final PayrollPeriodEarnerRankingJpaRepository rankingJpaRepository;

    @Override
    public List<AggregateRowProjection> queryAggregates(
            List<UUID> periodIds,
            List<GroupKey> groupKeys,
            ReportEffectiveFilters filters) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<PayrollPeriodAggregateEntity> root =
                cq.from(PayrollPeriodAggregateEntity.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(root.get("payrollPeriodId").in(periodIds));
        addFilter(cb, root, predicates, "countryId", filters.countryIds());
        addFilter(cb, root, predicates, "departmentId", filters.departmentIds());
        addFilter(cb, root, predicates, "roleId", filters.roleIds());
        if (filters.employmentTypes() != null && !filters.employmentTypes().isEmpty()) {
            predicates.add(root.get("employmentType").in(filters.employmentTypes()));
        }

        List<Selection<?>> selections = new ArrayList<>();
        List<Expression<?>> groupBy = new ArrayList<>();
        List<Order> orders = new ArrayList<>();

        for (GroupKey key : groupKeys) {
            switch (key) {
                case COUNTRY -> {
                    Root<CountryEntity> country = cq.from(CountryEntity.class);
                    predicates.add(cb.equal(country.get("id"), root.get("countryId")));
                    selections.add(country.get("isoCode").alias("countryCode"));
                    selections.add(country.get("name").alias("countryName"));
                    groupBy.add(country.get("id"));
                    groupBy.add(country.get("isoCode"));
                    groupBy.add(country.get("name"));
                    orders.add(cb.asc(country.get("isoCode")));
                }
                case DEPARTMENT -> {
                    Root<DepartmentEntity> dept = cq.from(DepartmentEntity.class);
                    predicates.add(cb.equal(dept.get("id"), root.get("departmentId")));
                    selections.add(dept.get("code").alias("departmentCode"));
                    selections.add(dept.get("name").alias("departmentName"));
                    groupBy.add(dept.get("code"));
                    groupBy.add(dept.get("name"));
                    orders.add(cb.asc(dept.get("name")));
                }
                case ROLE -> {
                    Root<RoleEntity> role = cq.from(RoleEntity.class);
                    predicates.add(cb.equal(role.get("id"), root.get("roleId")));
                    selections.add(role.get("code").alias("roleCode"));
                    selections.add(role.get("name").alias("roleName"));
                    groupBy.add(role.get("code"));
                    groupBy.add(role.get("name"));
                    orders.add(cb.asc(role.get("name")));
                }
                case EMPLOYMENT_TYPE -> {
                    Path<?> path = root.get("employmentType");
                    selections.add(path.alias("employmentType"));
                    groupBy.add(path);
                    orders.add(cb.asc(path));
                }
                case CURRENCY -> {
                    Path<?> path = root.get("referenceAmounts").get("currency");
                    selections.add(path.alias("currency"));
                    groupBy.add(path);
                    orders.add(cb.asc(path));
                }
                default -> throw new IllegalStateException("Unsupported group key: " + key);
            }
        }

        selections.add(cb.sum(root.get("referenceAmounts").get("base")).alias("sumBase"));
        selections.add(cb.sum(root.get("referenceAmounts").get("variable")).alias("sumVariable"));
        selections.add(cb.sum(root.get("referenceAmounts").get("allowances"))
                .alias("sumAllowances"));
        selections.add(cb.sum(root.get("referenceAmounts").get("employerTaxes"))
                .alias("sumEmployerTaxes"));
        selections.add(cb.sum(root.get("referenceAmounts").get("total")).alias("sumTotal"));
        selections.add(cb.sum(root.get("employeeCount")).alias("sumEmployeeCount"));

        cq.multiselect(selections);
        cq.where(cb.and(predicates.toArray(new Predicate[0])));
        cq.groupBy(groupBy);
        cq.orderBy(orders);

        List<Tuple> tuples = entityManager.createQuery(cq).getResultList();
        List<AggregateRowProjection> rows = new ArrayList<>(tuples.size());
        for (Tuple t : tuples) {
            rows.add(toAggregateProjection(t, groupKeys));
        }
        return rows;
    }

    @Override
    public List<AggregateRowProjection> queryStatistics(
            List<UUID> periodIds,
            List<GroupKey> groupKeys,
            ReportEffectiveFilters filters,
            String referenceCurrencyCode) {
        List<StatisticsRow> rows = rankingJpaRepository.minMaxByGroup(
                periodIds,
                referenceCurrencyCode,
                nullIfEmpty(filters.countryIds()),
                nullIfEmpty(filters.departmentIds()),
                nullIfEmpty(filters.roleIds()),
                nullIfEmpty(filters.employmentTypes()));

        List<AggregateRowProjection> projections = new ArrayList<>(rows.size());
        for (StatisticsRow row : rows) {
            projections.add(toStatisticsProjection(row, groupKeys));
        }
        return projections;
    }

    private AggregateRowProjection toStatisticsProjection(StatisticsRow row,
                                                          List<GroupKey> groupKeys) {
        AggregateRowProjection p = new AggregateRowProjection();
        for (GroupKey key : groupKeys) {
            switch (key) {
                case COUNTRY -> p.country = new RefDto(row.getCountryCode(),
                        row.getCountryName());
                case DEPARTMENT -> p.department = new RefDto(
                        String.valueOf(row.getDepartmentCode()),
                        row.getDepartmentName());
                case ROLE -> p.role = new RefDto(
                        String.valueOf(row.getRoleCode()),
                        row.getRoleName());
                case EMPLOYMENT_TYPE -> p.employmentType =
                        EmploymentType.valueOf(row.getEmploymentType());
                default -> throw new IllegalStateException(
                        "Unsupported statistics key: " + key);
            }
        }
        p.minReferenceAmount = row.getMinReferenceAmount()!= null ? row.getMinReferenceAmount().setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP): null;
        p.maxReferenceAmount = row.getMaxReferenceAmount()!= null ? row.getMaxReferenceAmount().setScale(AmountUtils.AMOUNT_SCALE, RoundingMode.HALF_UP): null;
        return p;
    }

    private <T> Collection<T> nullIfEmpty(Set<T> set) {
        return set == null || set.isEmpty() ? null : set;
    }

    private AggregateRowProjection toAggregateProjection(Tuple t, List<GroupKey> groupKeys) {
        AggregateRowProjection p = new AggregateRowProjection();
        for (GroupKey key : groupKeys) {
            switch (key) {
                case COUNTRY -> p.country = ref(t, "countryCode", "countryName");
                case DEPARTMENT -> p.department = ref(t, "departmentCode", "departmentName");
                case ROLE -> p.role = ref(t, "roleCode", "roleName");
                case EMPLOYMENT_TYPE -> p.employmentType =
                        t.get("employmentType", EmploymentType.class);
                case CURRENCY -> p.currency = t.get("currency", String.class);
                default -> throw new IllegalStateException("Unsupported group key: " + key);
            }
        }
        p.sumBase = t.get("sumBase", BigDecimal.class);
        p.sumVariable = t.get("sumVariable", BigDecimal.class);
        p.sumAllowances = t.get("sumAllowances", BigDecimal.class);
        p.sumEmployerTaxes = t.get("sumEmployerTaxes", BigDecimal.class);
        p.sumTotal = t.get("sumTotal", BigDecimal.class);
        Number count = (Number) t.get("sumEmployeeCount");
        p.sumEmployeeCount = count == null ? 0L : count.longValue();
        return p;
    }

    private RefDto ref(Tuple t, String codeAlias, String nameAlias) {
        String code = t.get(codeAlias, String.class);
        String name = t.get(nameAlias, String.class);
        if (code == null && name == null) {
            return null;
        }
        return new RefDto(code, name);
    }

    private void addFilter(CriteriaBuilder cb, Root<?> root, List<Predicate> predicates,
                           String field, Set<UUID> values) {
        if (values != null && !values.isEmpty()) {
            predicates.add(root.get(field).in(values));
        }
    }
}