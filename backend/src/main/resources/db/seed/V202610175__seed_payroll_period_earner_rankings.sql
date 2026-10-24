-- Seed: payroll period earner rankings.
--
-- Six partition kinds, matching what SnapshotBuilder writes:
--   c000  FULL              (country, department, role, employment_type)
--   c001  COUNTRY           (country only)
--   c002  DEPARTMENT        (department only)
--   c003  ROLE              (role only)
--   c004  EMPLOYMENT_TYPE   (employment_type only)
--   c005  GLOBAL            (no dimension)
--
-- For each partition and direction, top 5 and bottom 5 by reference amount.
-- Threshold: at least 10 VALIDATED summaries per partition, so every ranked
-- partition produces a full top-5 and bottom-5 with no gaps and no overlap.
--
-- Amounts: original in the assignment currency, reference converted with
-- total * reference_amount / rate. Reference currency is identity.
--
-- status = 'VALIDATED' on every row.
--
-- Ids: printf('%08x-0000-4000-<kind>-%012x', ...), where <kind> is the c0NN
-- above and the second %x is a ROW_NUMBER() over the ordering of the entries.

-- ===========================================================================
-- c000: FULL partition
-- ===========================================================================
WITH
ref AS (
  SELECT reference_currency_code AS code FROM currency_rates LIMIT 1
),
qualifying_groups AS (
  SELECT s.payroll_period_id, s.assignment_country_id, s.assignment_department_id,
         s.assignment_role_id, s.assignment_employment_type
  FROM payroll_summaries s WHERE s.status = 'VALIDATED'
  GROUP BY s.payroll_period_id, s.assignment_country_id, s.assignment_department_id,
           s.assignment_role_id, s.assignment_employment_type
  HAVING COUNT(*) >= 10
),
ranked_source AS (
  SELECT s.payroll_period_id, s.assignment_country_id, s.assignment_department_id,
         s.assignment_role_id, s.assignment_employment_type, s.employee_id,
         s.total AS original_amount, s.currency AS original_currency,
         CASE WHEN s.currency = ref.code THEN s.total
              ELSE s.total * cr.reference_amount / cr.rate END AS reference_amount,
         ref.code AS reference_currency,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_country_id,
                        s.assignment_department_id, s.assignment_role_id,
                        s.assignment_employment_type
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END DESC,
                    s.employee_id ASC) AS top_rank,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_country_id,
                        s.assignment_department_id, s.assignment_role_id,
                        s.assignment_employment_type
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END ASC,
                    s.employee_id ASC) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q
    ON q.payroll_period_id = s.payroll_period_id
   AND q.assignment_country_id = s.assignment_country_id
   AND q.assignment_department_id = s.assignment_department_id
   AND q.assignment_role_id = s.assignment_role_id
   AND q.assignment_employment_type = s.assignment_employment_type
  CROSS JOIN ref
  LEFT JOIN currency_rates cr
    ON cr.currency_code = s.currency AND cr.reference_currency_code = ref.code
  WHERE s.status = 'VALIDATED'
    AND (s.currency = ref.code OR cr.rate IS NOT NULL)
),
entries AS (
  SELECT r.payroll_period_id, r.assignment_country_id, r.assignment_department_id,
         r.assignment_role_id, r.assignment_employment_type,
         'TOP' AS direction, r.top_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.top_rank <= 5
  UNION ALL
  SELECT r.payroll_period_id, r.assignment_country_id, r.assignment_department_id,
         r.assignment_role_id, r.assignment_employment_type,
         'BOTTOM' AS direction, r.bottom_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency)
SELECT
  printf('%08x-0000-4000-c000-%012x',
         ROW_NUMBER() OVER (ORDER BY e.payroll_period_id, e.assignment_country_id,
                                     e.assignment_department_id, e.assignment_role_id,
                                     e.assignment_employment_type, e.direction, e.rank), 0),
  e.payroll_period_id, 'VALIDATED',
  e.assignment_country_id, e.assignment_department_id, e.assignment_role_id,
  e.assignment_employment_type, e.direction, e.rank, e.employee_id,
  e.original_amount, e.original_currency, e.reference_amount, e.reference_currency
FROM entries e;

-- ===========================================================================
-- c001: COUNTRY partition
-- ===========================================================================
WITH
ref AS (SELECT reference_currency_code AS code FROM currency_rates LIMIT 1),
qualifying_groups AS (
  SELECT s.payroll_period_id, s.assignment_country_id
  FROM payroll_summaries s WHERE s.status = 'VALIDATED'
  GROUP BY s.payroll_period_id, s.assignment_country_id
  HAVING COUNT(*) >= 10
),
ranked_source AS (
  SELECT s.payroll_period_id, s.assignment_country_id, s.employee_id,
         s.total AS original_amount, s.currency AS original_currency,
         CASE WHEN s.currency = ref.code THEN s.total
              ELSE s.total * cr.reference_amount / cr.rate END AS reference_amount,
         ref.code AS reference_currency,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_country_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END DESC,
                    s.employee_id ASC) AS top_rank,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_country_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END ASC,
                    s.employee_id ASC) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q
    ON q.payroll_period_id = s.payroll_period_id
   AND q.assignment_country_id = s.assignment_country_id
  CROSS JOIN ref
  LEFT JOIN currency_rates cr
    ON cr.currency_code = s.currency AND cr.reference_currency_code = ref.code
  WHERE s.status = 'VALIDATED'
    AND (s.currency = ref.code OR cr.rate IS NOT NULL)
),
entries AS (
  SELECT r.payroll_period_id, r.assignment_country_id,
         'TOP' AS direction, r.top_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.top_rank <= 5
  UNION ALL
  SELECT r.payroll_period_id, r.assignment_country_id,
         'BOTTOM' AS direction, r.bottom_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency)
SELECT
  printf('%08x-0000-4000-c001-%012x',
         ROW_NUMBER() OVER (ORDER BY e.payroll_period_id, e.assignment_country_id,
                                     e.direction, e.rank), 0),
  e.payroll_period_id, 'VALIDATED',
  e.assignment_country_id, NULL, NULL, NULL,
  e.direction, e.rank, e.employee_id,
  e.original_amount, e.original_currency, e.reference_amount, e.reference_currency
FROM entries e;

-- ===========================================================================
-- c002: DEPARTMENT partition
-- ===========================================================================
WITH
ref AS (SELECT reference_currency_code AS code FROM currency_rates LIMIT 1),
qualifying_groups AS (
  SELECT s.payroll_period_id, s.assignment_department_id
  FROM payroll_summaries s WHERE s.status = 'VALIDATED'
  GROUP BY s.payroll_period_id, s.assignment_department_id
  HAVING COUNT(*) >= 10
),
ranked_source AS (
  SELECT s.payroll_period_id, s.assignment_department_id, s.employee_id,
         s.total AS original_amount, s.currency AS original_currency,
         CASE WHEN s.currency = ref.code THEN s.total
              ELSE s.total * cr.reference_amount / cr.rate END AS reference_amount,
         ref.code AS reference_currency,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_department_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END DESC,
                    s.employee_id ASC) AS top_rank,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_department_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END ASC,
                    s.employee_id ASC) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q
    ON q.payroll_period_id = s.payroll_period_id
   AND q.assignment_department_id = s.assignment_department_id
  CROSS JOIN ref
  LEFT JOIN currency_rates cr
    ON cr.currency_code = s.currency AND cr.reference_currency_code = ref.code
  WHERE s.status = 'VALIDATED'
    AND (s.currency = ref.code OR cr.rate IS NOT NULL)
),
entries AS (
  SELECT r.payroll_period_id, r.assignment_department_id,
         'TOP' AS direction, r.top_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.top_rank <= 5
  UNION ALL
  SELECT r.payroll_period_id, r.assignment_department_id,
         'BOTTOM' AS direction, r.bottom_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency)
SELECT
  printf('%08x-0000-4000-c002-%012x',
         ROW_NUMBER() OVER (ORDER BY e.payroll_period_id, e.assignment_department_id,
                                     e.direction, e.rank), 0),
  e.payroll_period_id, 'VALIDATED',
  NULL, e.assignment_department_id, NULL, NULL,
  e.direction, e.rank, e.employee_id,
  e.original_amount, e.original_currency, e.reference_amount, e.reference_currency
FROM entries e;

-- ===========================================================================
-- c003: ROLE partition
-- ===========================================================================
WITH
ref AS (SELECT reference_currency_code AS code FROM currency_rates LIMIT 1),
qualifying_groups AS (
  SELECT s.payroll_period_id, s.assignment_role_id
  FROM payroll_summaries s WHERE s.status = 'VALIDATED'
  GROUP BY s.payroll_period_id, s.assignment_role_id
  HAVING COUNT(*) >= 10
),
ranked_source AS (
  SELECT s.payroll_period_id, s.assignment_role_id, s.employee_id,
         s.total AS original_amount, s.currency AS original_currency,
         CASE WHEN s.currency = ref.code THEN s.total
              ELSE s.total * cr.reference_amount / cr.rate END AS reference_amount,
         ref.code AS reference_currency,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_role_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END DESC,
                    s.employee_id ASC) AS top_rank,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_role_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END ASC,
                    s.employee_id ASC) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q
    ON q.payroll_period_id = s.payroll_period_id
   AND q.assignment_role_id = s.assignment_role_id
  CROSS JOIN ref
  LEFT JOIN currency_rates cr
    ON cr.currency_code = s.currency AND cr.reference_currency_code = ref.code
  WHERE s.status = 'VALIDATED'
    AND (s.currency = ref.code OR cr.rate IS NOT NULL)
),
entries AS (
  SELECT r.payroll_period_id, r.assignment_role_id,
         'TOP' AS direction, r.top_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.top_rank <= 5
  UNION ALL
  SELECT r.payroll_period_id, r.assignment_role_id,
         'BOTTOM' AS direction, r.bottom_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency)
SELECT
  printf('%08x-0000-4000-c003-%012x',
         ROW_NUMBER() OVER (ORDER BY e.payroll_period_id, e.assignment_role_id,
                                     e.direction, e.rank), 0),
  e.payroll_period_id, 'VALIDATED',
  NULL, NULL, e.assignment_role_id, NULL,
  e.direction, e.rank, e.employee_id,
  e.original_amount, e.original_currency, e.reference_amount, e.reference_currency
FROM entries e;

-- ===========================================================================
-- c004: EMPLOYMENT_TYPE partition
-- ===========================================================================
WITH
ref AS (SELECT reference_currency_code AS code FROM currency_rates LIMIT 1),
qualifying_groups AS (
  SELECT s.payroll_period_id, s.assignment_employment_type
  FROM payroll_summaries s WHERE s.status = 'VALIDATED'
  GROUP BY s.payroll_period_id, s.assignment_employment_type
  HAVING COUNT(*) >= 10
),
ranked_source AS (
  SELECT s.payroll_period_id, s.assignment_employment_type, s.employee_id,
         s.total AS original_amount, s.currency AS original_currency,
         CASE WHEN s.currency = ref.code THEN s.total
              ELSE s.total * cr.reference_amount / cr.rate END AS reference_amount,
         ref.code AS reference_currency,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_employment_type
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END DESC,
                    s.employee_id ASC) AS top_rank,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id, s.assignment_employment_type
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END ASC,
                    s.employee_id ASC) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q
    ON q.payroll_period_id = s.payroll_period_id
   AND q.assignment_employment_type = s.assignment_employment_type
  CROSS JOIN ref
  LEFT JOIN currency_rates cr
    ON cr.currency_code = s.currency AND cr.reference_currency_code = ref.code
  WHERE s.status = 'VALIDATED'
    AND (s.currency = ref.code OR cr.rate IS NOT NULL)
),
entries AS (
  SELECT r.payroll_period_id, r.assignment_employment_type,
         'TOP' AS direction, r.top_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.top_rank <= 5
  UNION ALL
  SELECT r.payroll_period_id, r.assignment_employment_type,
         'BOTTOM' AS direction, r.bottom_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency)
SELECT
  printf('%08x-0000-4000-c004-%012x',
         ROW_NUMBER() OVER (ORDER BY e.payroll_period_id, e.assignment_employment_type,
                                     e.direction, e.rank), 0),
  e.payroll_period_id, 'VALIDATED',
  NULL, NULL, NULL, e.assignment_employment_type,
  e.direction, e.rank, e.employee_id,
  e.original_amount, e.original_currency, e.reference_amount, e.reference_currency
FROM entries e;

-- ===========================================================================
-- c005: GLOBAL partition
-- ===========================================================================
WITH
ref AS (SELECT reference_currency_code AS code FROM currency_rates LIMIT 1),
qualifying_groups AS (
  SELECT s.payroll_period_id
  FROM payroll_summaries s WHERE s.status = 'VALIDATED'
  GROUP BY s.payroll_period_id
  HAVING COUNT(*) >= 10
),
ranked_source AS (
  SELECT s.payroll_period_id, s.employee_id,
         s.total AS original_amount, s.currency AS original_currency,
         CASE WHEN s.currency = ref.code THEN s.total
              ELSE s.total * cr.reference_amount / cr.rate END AS reference_amount,
         ref.code AS reference_currency,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END DESC,
                    s.employee_id ASC) AS top_rank,
         ROW_NUMBER() OVER (
           PARTITION BY s.payroll_period_id
           ORDER BY CASE WHEN s.currency = ref.code THEN s.total
                         ELSE s.total * cr.reference_amount / cr.rate END ASC,
                    s.employee_id ASC) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q ON q.payroll_period_id = s.payroll_period_id
  CROSS JOIN ref
  LEFT JOIN currency_rates cr
    ON cr.currency_code = s.currency AND cr.reference_currency_code = ref.code
  WHERE s.status = 'VALIDATED'
    AND (s.currency = ref.code OR cr.rate IS NOT NULL)
),
entries AS (
  SELECT r.payroll_period_id,
         'TOP' AS direction, r.top_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.top_rank <= 5
  UNION ALL
  SELECT r.payroll_period_id,
         'BOTTOM' AS direction, r.bottom_rank AS rank, r.employee_id,
         r.original_amount, r.original_currency, r.reference_amount, r.reference_currency
  FROM ranked_source r WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency)
SELECT
  printf('%08x-0000-4000-c005-%012x',
         ROW_NUMBER() OVER (ORDER BY e.payroll_period_id, e.direction, e.rank), 0),
  e.payroll_period_id, 'VALIDATED',
  NULL, NULL, NULL, NULL,
  e.direction, e.rank, e.employee_id,
  e.original_amount, e.original_currency, e.reference_amount, e.reference_currency
FROM entries e;