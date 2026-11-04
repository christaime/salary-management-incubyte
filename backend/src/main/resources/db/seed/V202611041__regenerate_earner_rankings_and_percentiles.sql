-- V202611041__regenerate_earner_rankings_and_percentiles.sql
--
-- Rebuild payroll_period_earner_rankings and
-- payroll_period_earner_percentiles from payroll_summaries.
--
-- The seed (V202610251__seed_payroll_period_earner_rankings.sql) has two
-- problems:
--
--   1. It links rows to the period the *summary* sits in, so a bi-weekly
--      summary's ranks/percentiles live under a bi-weekly period id. The
--      UI queries rows by the *monthly* period id. This migration links
--      every rank/percentile row to the monthly period's id.
--
--   2. It annualizes against a hardcoded 31-day month and stores a monthly
--      rate in a column declared annualized. This migration uses the target
--      period's actual length for the scale-up and multiplies by 12 for the
--      annualized percentile column.
--
-- The population for a monthly target period T is every VALIDATED summary
-- whose own period's start_date is inside [T.start_date, T.end_date).
--
-- For each (T, employee):
--
--   daysCovered   = SUM of the summary's own period length
--                   (summary_period.end_date - summary_period.start_date + 1)
--   totalAmount   = SUM of the summary's total, converted to the reference
--                   currency
--   daysInT       = T.end_date - T.start_date + 1
--   monthlyRate   = (totalAmount / daysCovered) * daysInT
--   annualized    = monthlyRate * 12
--
-- Ranks: TOP 5 and BOTTOM 5 per (T, partition) where the partition has
-- at least 10 employees. Partitions:
--   c000  FULL              (country, department, role, employment_type)
--   c001  COUNTRY           (country only)
--   c002  DEPARTMENT        (department only)
--   c003  ROLE              (role only)
--   c004  EMPLOYMENT_TYPE   (employment_type only)
--   c005  GLOBAL            (no dimension)
--
-- Percentiles: markers 0, 10, 25, 50, 75, 90, 100 per (T, partition),
-- where marker p is the employee at rank CEIL(n * p / 100), clamped to
-- >= 1. Same partitions. Groups of at least 10 employees.
--
-- Ids: 8-4-4-12 hex. Rankings use the 4th block to encode kind+direction:
--   0xC0N1 (TOP) / 0xC0N2 (BOTTOM), N = 0..5.
-- Percentiles use the 4th block to encode kind+E:
--   0xC0E0 (c000) through 0xC0E5 (c005).

-------------------------------------------------------------------------------
-- 0. Wipe.
-------------------------------------------------------------------------------
DELETE FROM payroll_period_earner_percentiles;
DELETE FROM payroll_period_earner_rankings;

-------------------------------------------------------------------------------
-- 1. Per (target, employee) aggregate.
-------------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_target_employee;

CREATE TEMP TABLE tmp_target_employee AS
SELECT
    t.id                            AS target_period_id,
    s.assignment_country_id         AS country_id,
    s.assignment_department_id      AS department_id,
    s.assignment_role_id            AS role_id,
    s.assignment_employment_type    AS employment_type,
    s.employee_id,
    SUM(
        CASE WHEN s.currency = ref.code THEN s.total
             ELSE s.total * cr.reference_amount / cr.rate
        END
    )                               AS reference_total,
    SUM(
        CAST(julianday(sp.end_date) - julianday(sp.start_date) AS INTEGER) + 1
    )                               AS days_covered,
    CAST(julianday(t.end_date) - julianday(t.start_date) AS INTEGER) + 1
                                    AS days_in_target,
    ref.code                        AS reference_currency
FROM payroll_periods t
    JOIN payroll_periods sp
        ON sp.start_date >= t.start_date
       AND sp.start_date <  t.end_date
    JOIN payroll_summaries s
        ON s.payroll_period_id = sp.id
       AND s.status = 'VALIDATED'
    CROSS JOIN (SELECT reference_currency_code AS code FROM currency_rates LIMIT 1) ref
    LEFT JOIN currency_rates cr
        ON cr.currency_code = s.currency
       AND cr.reference_currency_code = ref.code
WHERE t.frequency = 'MONTHLY'
  AND (s.currency = ref.code OR cr.rate IS NOT NULL)
GROUP BY
    t.id,
    s.assignment_country_id, s.assignment_department_id,
    s.assignment_role_id, s.assignment_employment_type,
    s.employee_id, ref.code;

-------------------------------------------------------------------------------
-- 2. Monthly rate and annualized per (target, employee).
-------------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_target_monthly;

CREATE TEMP TABLE tmp_target_monthly AS
SELECT
    target_period_id,
    country_id,
    department_id,
    role_id,
    employment_type,
    employee_id,
    reference_total,
    days_covered,
    reference_currency,
    CASE
        WHEN days_covered = 0 THEN NULL
        ELSE (reference_total / days_covered) * days_in_target
    END                             AS monthly_rate
FROM tmp_target_employee;

-------------------------------------------------------------------------------
-- 3. Rankings — one INSERT per partition per direction.
--    Id 4th block: 0xC0N1 (TOP) / 0xC0N2 (BOTTOM).
-------------------------------------------------------------------------------

-- c000: FULL, TOP
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.country_id,
                                       r.department_id, r.role_id, r.employment_type,
                                       'TOP', t.rn),
           0xc001, t.rn),
    r.target_period_id, 'VALIDATED',
    r.country_id, r.department_id, r.role_id, r.employment_type,
    'TOP', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, country_id, department_id, role_id,
           employment_type,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, country_id, department_id,
                            role_id, employment_type
               ORDER BY monthly_rate DESC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (
               PARTITION BY target_period_id, country_id, department_id,
                            role_id, employment_type
           ) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.country_id IS r.country_id)
   AND (t.department_id IS r.department_id)
   AND (t.role_id IS r.role_id)
   AND (t.employment_type IS r.employment_type)
WHERE t.rn <= 5 AND t.n >= 10;

-- c000: FULL, BOTTOM
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.country_id,
                                       r.department_id, r.role_id, r.employment_type,
                                       'BOTTOM', t.rn),
           0xc002, t.rn),
    r.target_period_id, 'VALIDATED',
    r.country_id, r.department_id, r.role_id, r.employment_type,
    'BOTTOM', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, country_id, department_id, role_id,
           employment_type,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, country_id, department_id,
                            role_id, employment_type
               ORDER BY monthly_rate ASC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (
               PARTITION BY target_period_id, country_id, department_id,
                            role_id, employment_type
           ) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.country_id IS r.country_id)
   AND (t.department_id IS r.department_id)
   AND (t.role_id IS r.role_id)
   AND (t.employment_type IS r.employment_type)
WHERE t.rn <= 5 AND t.n >= 10;

-- c001: COUNTRY, TOP
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.country_id,
                                       'TOP', t.rn),
           0xc011, t.rn),
    r.target_period_id, 'VALIDATED',
    r.country_id, NULL, NULL, NULL,
    'TOP', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, country_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, country_id
               ORDER BY monthly_rate DESC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, country_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.country_id IS r.country_id)
WHERE t.rn <= 5 AND t.n >= 10;

-- c001: COUNTRY, BOTTOM
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.country_id,
                                       'BOTTOM', t.rn),
           0xc012, t.rn),
    r.target_period_id, 'VALIDATED',
    r.country_id, NULL, NULL, NULL,
    'BOTTOM', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, country_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, country_id
               ORDER BY monthly_rate ASC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, country_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.country_id IS r.country_id)
WHERE t.rn <= 5 AND t.n >= 10;

-- c002: DEPARTMENT, TOP
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.department_id,
                                       'TOP', t.rn),
           0xc021, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, r.department_id, NULL, NULL,
    'TOP', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, department_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, department_id
               ORDER BY monthly_rate DESC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, department_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.department_id IS r.department_id)
WHERE t.rn <= 5 AND t.n >= 10;

-- c002: DEPARTMENT, BOTTOM
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.department_id,
                                       'BOTTOM', t.rn),
           0xc022, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, r.department_id, NULL, NULL,
    'BOTTOM', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, department_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, department_id
               ORDER BY monthly_rate ASC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, department_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.department_id IS r.department_id)
WHERE t.rn <= 5 AND t.n >= 10;

-- c003: ROLE, TOP
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.role_id,
                                       'TOP', t.rn),
           0xc031, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, r.role_id, NULL,
    'TOP', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, role_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, role_id
               ORDER BY monthly_rate DESC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, role_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.role_id IS r.role_id)
WHERE t.rn <= 5 AND t.n >= 10;

-- c003: ROLE, BOTTOM
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.role_id,
                                       'BOTTOM', t.rn),
           0xc032, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, r.role_id, NULL,
    'BOTTOM', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, role_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, role_id
               ORDER BY monthly_rate ASC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, role_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.role_id IS r.role_id)
WHERE t.rn <= 5 AND t.n >= 10;

-- c004: EMPLOYMENT_TYPE, TOP
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.employment_type,
                                       'TOP', t.rn),
           0xc041, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, NULL, r.employment_type,
    'TOP', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, employment_type,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, employment_type
               ORDER BY monthly_rate DESC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, employment_type) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.employment_type IS r.employment_type)
WHERE t.rn <= 5 AND t.n >= 10;

-- c004: EMPLOYMENT_TYPE, BOTTOM
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.employment_type,
                                       'BOTTOM', t.rn),
           0xc042, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, NULL, r.employment_type,
    'BOTTOM', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id, employment_type,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id, employment_type
               ORDER BY monthly_rate ASC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id, employment_type) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
   AND (t.employment_type IS r.employment_type)
WHERE t.rn <= 5 AND t.n >= 10;

-- c005: GLOBAL, TOP
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, 'TOP', t.rn),
           0xc051, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, NULL, NULL,
    'TOP', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id
               ORDER BY monthly_rate DESC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
WHERE t.rn <= 5 AND t.n >= 10;

-- c005: GLOBAL, BOTTOM
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, 'BOTTOM', t.rn),
           0xc052, t.rn),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, NULL, NULL,
    'BOTTOM', t.rn, r.employee_id,
    r.reference_total, r.reference_currency, r.reference_total, r.reference_currency,
    r.days_covered, ROUND(r.monthly_rate, 2), r.reference_currency
FROM tmp_target_monthly r
JOIN (
    SELECT target_period_id, employee_id,
           ROW_NUMBER() OVER (
               PARTITION BY target_period_id
               ORDER BY monthly_rate ASC, employee_id ASC
           ) AS rn,
           COUNT(*) OVER (PARTITION BY target_period_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) t ON t.target_period_id = r.target_period_id
   AND t.employee_id = r.employee_id
WHERE t.rn <= 5 AND t.n >= 10;

-------------------------------------------------------------------------------
-- 4. Percentiles — one INSERT per partition. Id 4th block = 0xC0E0..0xC0E5.
--    Marker p's row is the employee at rank CEIL(n * p / 100) (min 1).
-- ---------------------------------------------------------------------------

-- c000: FULL
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile, reference_amount, reference_currency,
     employee_count)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.country_id,
                                       r.department_id, r.role_id, r.employment_type, p),
           0xc0e0, p),
    r.target_period_id, 'VALIDATED',
    r.country_id, r.department_id, r.role_id, r.employment_type,
    p, r.monthly_rate * 12, r.reference_currency, r.n
FROM (
    SELECT
        target_period_id, country_id, department_id, role_id, employment_type,
        employee_id, monthly_rate, reference_currency,
        ROW_NUMBER() OVER (
            PARTITION BY target_period_id, country_id, department_id, role_id, employment_type
            ORDER BY monthly_rate ASC, employee_id ASC
        ) AS rn,
        COUNT(*) OVER (
            PARTITION BY target_period_id, country_id, department_id, role_id, employment_type
        ) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) r
CROSS JOIN (SELECT 0 AS p UNION ALL SELECT 10 UNION ALL SELECT 25 UNION ALL
                   SELECT 50 UNION ALL SELECT 75 UNION ALL SELECT 90 UNION ALL
                   SELECT 100) pct
WHERE r.n >= 10
  AND r.rn = CASE
               WHEN CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER) < 1 THEN 1
               ELSE CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER)
             END;

-- c001: COUNTRY
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile, reference_amount, reference_currency,
     employee_count)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.country_id, p),
           0xc0e1, p),
    r.target_period_id, 'VALIDATED',
    r.country_id, NULL, NULL, NULL,
    p, r.monthly_rate * 12, r.reference_currency, r.n
FROM (
    SELECT
        target_period_id, country_id, employee_id, monthly_rate, reference_currency,
        ROW_NUMBER() OVER (
            PARTITION BY target_period_id, country_id
            ORDER BY monthly_rate ASC, employee_id ASC
        ) AS rn,
        COUNT(*) OVER (PARTITION BY target_period_id, country_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) r
CROSS JOIN (SELECT 0 AS p UNION ALL SELECT 10 UNION ALL SELECT 25 UNION ALL
                   SELECT 50 UNION ALL SELECT 75 UNION ALL SELECT 90 UNION ALL
                   SELECT 100) pct
WHERE r.n >= 10
  AND r.rn = CASE
               WHEN CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER) < 1 THEN 1
               ELSE CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER)
             END;

-- c002: DEPARTMENT
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile, reference_amount, reference_currency,
     employee_count)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.department_id, p),
           0xc0e2, p),
    r.target_period_id, 'VALIDATED',
    NULL, r.department_id, NULL, NULL,
    p, r.monthly_rate * 12, r.reference_currency, r.n
FROM (
    SELECT
        target_period_id, department_id, employee_id, monthly_rate, reference_currency,
        ROW_NUMBER() OVER (
            PARTITION BY target_period_id, department_id
            ORDER BY monthly_rate ASC, employee_id ASC
        ) AS rn,
        COUNT(*) OVER (PARTITION BY target_period_id, department_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) r
CROSS JOIN (SELECT 0 AS p UNION ALL SELECT 10 UNION ALL SELECT 25 UNION ALL
                   SELECT 50 UNION ALL SELECT 75 UNION ALL SELECT 90 UNION ALL
                   SELECT 100) pct
WHERE r.n >= 10
  AND r.rn = CASE
               WHEN CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER) < 1 THEN 1
               ELSE CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER)
             END;

-- c003: ROLE
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile, reference_amount, reference_currency,
     employee_count)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.role_id, p),
           0xc0e3, p),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, r.role_id, NULL,
    p, r.monthly_rate * 12, r.reference_currency, r.n
FROM (
    SELECT
        target_period_id, role_id, employee_id, monthly_rate, reference_currency,
        ROW_NUMBER() OVER (
            PARTITION BY target_period_id, role_id
            ORDER BY monthly_rate ASC, employee_id ASC
        ) AS rn,
        COUNT(*) OVER (PARTITION BY target_period_id, role_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) r
CROSS JOIN (SELECT 0 AS p UNION ALL SELECT 10 UNION ALL SELECT 25 UNION ALL
                   SELECT 50 UNION ALL SELECT 75 UNION ALL SELECT 90 UNION ALL
                   SELECT 100) pct
WHERE r.n >= 10
  AND r.rn = CASE
               WHEN CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER) < 1 THEN 1
               ELSE CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER)
             END;

-- c004: EMPLOYMENT_TYPE
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile, reference_amount, reference_currency,
     employee_count)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, r.employment_type, p),
           0xc0e4, p),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, NULL, r.employment_type,
    p, r.monthly_rate * 12, r.reference_currency, r.n
FROM (
    SELECT
        target_period_id, employment_type, employee_id, monthly_rate, reference_currency,
        ROW_NUMBER() OVER (
            PARTITION BY target_period_id, employment_type
            ORDER BY monthly_rate ASC, employee_id ASC
        ) AS rn,
        COUNT(*) OVER (PARTITION BY target_period_id, employment_type) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) r
CROSS JOIN (SELECT 0 AS p UNION ALL SELECT 10 UNION ALL SELECT 25 UNION ALL
                   SELECT 50 UNION ALL SELECT 75 UNION ALL SELECT 90 UNION ALL
                   SELECT 100) pct
WHERE r.n >= 10
  AND r.rn = CASE
               WHEN CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER) < 1 THEN 1
               ELSE CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER)
             END;

-- c005: GLOBAL
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile, reference_amount, reference_currency,
     employee_count)
SELECT
    printf('%08x-0000-4000-%04x-%012x',
           ROW_NUMBER() OVER (ORDER BY r.target_period_id, p),
           0xc0e5, p),
    r.target_period_id, 'VALIDATED',
    NULL, NULL, NULL, NULL,
    p, r.monthly_rate * 12, r.reference_currency, r.n
FROM (
    SELECT
        target_period_id, employee_id, monthly_rate, reference_currency,
        ROW_NUMBER() OVER (
            PARTITION BY target_period_id
            ORDER BY monthly_rate ASC, employee_id ASC
        ) AS rn,
        COUNT(*) OVER (PARTITION BY target_period_id) AS n
    FROM tmp_target_monthly
    WHERE monthly_rate IS NOT NULL
) r
CROSS JOIN (SELECT 0 AS p UNION ALL SELECT 10 UNION ALL SELECT 25 UNION ALL
                   SELECT 50 UNION ALL SELECT 75 UNION ALL SELECT 90 UNION ALL
                   SELECT 100) pct
WHERE r.n >= 10
  AND r.rn = CASE
               WHEN CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER) < 1 THEN 1
               ELSE CAST(CEIL(r.n * pct.p / 100.0) AS INTEGER)
             END;

-------------------------------------------------------------------------------
-- 5. Cleanup.
-------------------------------------------------------------------------------
DROP TABLE IF EXISTS tmp_target_monthly;
DROP TABLE IF EXISTS tmp_target_employee;