-- Seed: payroll period earner rankings, top 5 and bottom 5 per group.
--
-- Source: payroll_summaries WHERE status = 'VALIDATED' (closed periods only).
-- Grouping: (payroll_period_id, country, department, role, employment_type),
--           the same tuple as payroll_period_aggregates.
-- Threshold: employee_count >= 10 per group, so every ranked group produces a
--           full top-5 and bottom-5 with no gaps and no overlap.
--
-- For each qualifying (period, group):
--   direction = 'TOP',    rank 1..5 by total DESC
--   direction = 'BOTTOM', rank 1..5 by total ASC
-- amount = the employee's total for the period, in the assignment currency.
-- status = 'VALIDATED' on every row.
--
-- Id: ROW_NUMBER() over the full (period, group, direction, rank) tuple. That
-- tuple is exactly uk_payroll_period_earner_ranking_group, so the ordering is
-- total and ids are deterministic across runs and SQLite versions.

WITH
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
-- Per-employee totals inside each qualifying group.
ranked_source AS (
  SELECT
    s.payroll_period_id,
    s.assignment_country_id,
    s.assignment_department_id,
    s.assignment_role_id,
    s.assignment_employment_type,
    s.employee_id,
    s.total,
    ROW_NUMBER() OVER (
      PARTITION BY s.payroll_period_id,
                   s.assignment_country_id,
                   s.assignment_department_id,
                   s.assignment_role_id,
                   s.assignment_employment_type
      ORDER BY s.total DESC, s.employee_id ASC
    ) AS top_rank,
    ROW_NUMBER() OVER (
      PARTITION BY s.payroll_period_id,
                   s.assignment_country_id,
                   s.assignment_department_id,
                   s.assignment_role_id,
                   s.assignment_employment_type
      ORDER BY s.total ASC, s.employee_id ASC
    ) AS bottom_rank
  FROM payroll_summaries s
  JOIN qualifying_groups q
    ON q.payroll_period_id          = s.payroll_period_id
   AND q.assignment_country_id      = s.assignment_country_id
   AND q.assignment_department_id   = s.assignment_department_id
   AND q.assignment_role_id         = s.assignment_role_id
   AND q.assignment_employment_type = s.assignment_employment_type
  WHERE s.status = 'VALIDATED'
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
    r.total AS amount
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
    r.total AS amount
  FROM ranked_source r
  WHERE r.bottom_rank <= 5
)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status,
     country_id, department_id, role_id, employment_type,
     direction, rank, employee_id, amount)
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
  e.amount
FROM entries e;