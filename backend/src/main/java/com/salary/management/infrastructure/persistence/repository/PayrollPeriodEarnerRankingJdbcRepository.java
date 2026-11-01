package com.salary.management.infrastructure.persistence.repository;

import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerPercentileEntity;
import com.salary.management.infrastructure.persistence.entity.PayrollPeriodEarnerRankingEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PayrollPeriodEarnerRankingJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_RANKING_SQL = """
            INSERT INTO payroll_period_earner_rankings
                (id, payroll_period_id, status, country_id, department_id, role_id,
                 employment_type, direction, rank, employee_id,
                 original_amount, original_currency,
                 reference_amount, reference_currency,
                 days_covered, monthly_rate_amount, monthly_rate_currency)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private static final String INSERT_PERCENTILE_SQL = """
        INSERT INTO payroll_period_earner_percentiles
            (id, payroll_period_id, country_id, department_id, role_id,
             employment_type, percentile, reference_amount, reference_currency,
             employee_count, status)
        VALUES (?,?,?,?,?,?,?,?,?,?,?)
        """;

    public void saveAll(List<PayrollPeriodEarnerRankingEntity> rankings) {
        if (rankings == null || rankings.isEmpty()) return;
        jdbcTemplate.batchUpdate(INSERT_RANKING_SQL, rankings, 100, (ps, r) -> {
            ps.setString(1, r.getId().toString());
            ps.setString(2, r.getPayrollPeriodId().toString());
            ps.setString(3, r.getStatus().name());
            ps.setString(4, uuidOrNull(r.getCountryId()));
            ps.setString(5, uuidOrNull(r.getDepartmentId()));
            ps.setString(6, uuidOrNull(r.getRoleId()));
            ps.setString(7, enumOrNull(r.getEmploymentType()));
            ps.setString(8, r.getDirection().name());
            ps.setInt(9, r.getRank());
            ps.setString(10, r.getEmployeeId().toString());
            ps.setBigDecimal(11, r.getOriginalAmount().getAmount());
            ps.setString(12, r.getOriginalAmount().getCurrency());
            ps.setBigDecimal(13, r.getReferenceAmount().getAmount());
            ps.setString(14, r.getReferenceAmount().getCurrency());
            ps.setInt(15, r.getDaysCovered());
            ps.setBigDecimal(16, r.getMonthlyRateAmount().getAmount());
            ps.setString(17, r.getMonthlyRateAmount().getCurrency());
        });
    }

    private static String uuidOrNull(UUID id) { return id == null ? null : id.toString(); }
    private static String enumOrNull(Enum<?> e) { return e == null ? null : e.name(); }

    public void saveAllPercentiles(List<PayrollPeriodEarnerPercentileEntity> percentiles) {
        if (percentiles == null || percentiles.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_PERCENTILE_SQL, percentiles, 100, (ps, p) -> {
            ps.setString(1, p.getId().toString());
            ps.setString(2, p.getPayrollPeriodId().toString());
            ps.setString(3, uuidOrNull(p.getCountryId()));
            ps.setString(4, uuidOrNull(p.getDepartmentId()));
            ps.setString(5, uuidOrNull(p.getRoleId()));
            ps.setString(6, p.getEmploymentType());
            ps.setInt(7, p.getPercentile());
            ps.setBigDecimal(8, p.getReferenceAmount());
            ps.setString(9, p.getReferenceCurrency());
            ps.setInt(10, p.getEmployeeCount());
            ps.setString(11, p.getStatus().name());
        });
    }
}