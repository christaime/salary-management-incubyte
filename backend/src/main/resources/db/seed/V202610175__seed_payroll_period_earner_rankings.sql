-- Seed: payroll period earner rankings, top 5 and bottom 5 per group.
--
-- Source: payroll_summaries WHERE status = 'VALIDATED' (closed periods only).
-- Grouping: (payroll_period_id, country, department, role, employment_type),
--           the same tuple as payroll_period_aggregates.
-- Threshold: employee_count >= 10 per group, so every ranked group produces a
--           full top-5 and bottom-5 with no gaps and no overlap.
--
-- For each qualifying (period, group):
--   direction = 'TOP',    rank 1..5 by reference amount DESC
--   direction = 'BOTTOM', rank 1..5 by reference amount ASC
--
-- Original amount: the employee's total for the period, in the assignment currency.
-- Reference amount: the same total converted to the reference currency via
--   currency_rates, using total * reference_amount / rate, per the schema comment.
--   The reference currency itself is used unchanged (identity conversion).
--
-- status = 'VALIDATED' on every row.
--
-- Id: ROW_NUMBER() over the full (period, group, direction, rank) tuple. That
-- tuple is exactly uk_payroll_period_earner_ranking_group, so the ordering is
-- total and ids are deterministic across runs and SQLite versions.

-- The reference currency is a single value across the whole table, enforced by
-- the triggers in V1. Empty table = no rankings can be produced; the select
-- below will produce no rows in that case.
WITH
ref AS (
  SELECT reference_currency_code AS code
  FROM currency_rates
  LIMIT 1
),
-- Restrict to groups with at least 10 VALIDATED summaries, so a full
-- top-5 + bottom-5 exists for every ranked group.
qualifying_groups AS (
  SELECT
    s.payroll_period_id,
    s.assignment_country_id,
    s.assignment_department_id,
    s.assignment_role_id,
    s.assignment_employment_type
  FROM payroll_summaries s
  WHERE s.status = 'VALIDATED'
  GROUP BY
    s.payroll_period_id,
    s.assignment_country_id,
    s.assignment_department_id,
    s.assignment_role_id,
    s.assignment_employment_type
  HAVING COUNT(*) >= 10
),
-- Per-employee rows inside each qualifying group, with the converted amount.
ranked_source AS (
  SELECT
    s.payroll_period_id,
    s.assignment_country_id,
    s.assignment_department_id,
    s.assignment_role_id,
    s.assignment_employment_type,
    s.employee_id,
    s.total        AS original_amount,
    s.currency     AS original_currency,
    CASE
      WHEN s.currency = ref.code THEN s.total
      ELSE s.total * cr.reference_amount / cr.rate
    END            AS reference_amount,
    ref.code       AS reference_currency,
    ROW_NUMBER() OVER (
      PARTITION BY s.payroll_period_id,
                   s.assignment_country_id,
                   s.assignment_department_id,
                   s.assignment_role_id,
                   s.assignment_employment_type
      ORDER BY
        CASE
          WHEN s.currency = ref.code THEN s.total
          ELSE s.total * cr.reference_amount / cr.rate
        END DESC,
        s.employee_id ASC
    ) AS top_rank,
    ROW_NUMBER() OVER (
      PARTITION BY s.payroll_period_id,
                   s.assignment_country_id,
                   s.assignment_department_id,
                   s.assignment_role_id,
                   s.assignment_employment_type
      ORDER BY
        CASE
          WHEN s.currency = ref.code THEN s.total
          ELSE s.total * cr.reference_amount / cr.rate
        END ASC,
        s.employee_id ASC
    ) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q
    ON q.payroll_period_id          = s.payroll_period_id
   AND q.assignment_country_id      = s.assignment_country_id
   AND q.assignment_department_id   = s.assignment_department_id
   AND q.assignment_role_id         = s.assignment_role_id
   AND q.assignment_employment_type = s.assignment_employment_type
  CROSS JOIN ref
  LEFT JOIN currency_rates cr
    ON cr.currency_code = s.currency
   AND cr.reference_currency_code = ref.code
  WHERE s.status = 'VALIDATED'
    AND (
         s.currency = ref.code
      OR cr.rate IS NOT NULL
    )
),
-- The actual ranking rows: top 1..5 and bottom 1..5.
entries AS (
  SELECT
    r.payroll_period_id,
    r.assignment_country_id,
    r.assignment_department_id,
    r.assignment_role_id,
    r.assignment_employment_type,
    'TOP' AS direction,
    r.top_rank AS rank,
    r.employee_id,
    r.original_amount,
    r.original_currency,
    r.reference_amount,
    r.reference_currency
  FROM ranked_source r
  WHERE r.top_rank <= 5
  UNION ALL
  SELECT
    r.payroll_period_id,
    r.assignment_country_id,
    r.assignment_department_id,
    r.assignment_role_id,
    r.assignment_employment_type,
    'BOTTOM' AS direction,
    r.bottom_rank AS rank,
    r.employee_id,
    r.original_amount,
    r.original_currency,
    r.reference_amount,
    r.reference_currency
  FROM ranked_source r
  WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status,
     country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency)
SELECT
  printf('%08x-0000-4000-c000-%012x',
         ROW_NUMBER() OVER (
           ORDER BY e.payroll_period_id,
                    e.assignment_country_id,
                    e.assignment_department_id,
                    e.assignment_role_id,
                    e.assignment_employment_type,
                    e.direction,
                    e.rank),
         0),
  e.payroll_period_id,
  'VALIDATED',
  e.assignment_country_id,
  e.assignment_department_id,
  e.assignment_role_id,
  e.assignment_employment_type,
  e.direction,
  e.rank,
  e.employee_id,
  e.original_amount,
  e.original_currency,
  e.reference_amount,
  e.reference_currency
FROM entries e;