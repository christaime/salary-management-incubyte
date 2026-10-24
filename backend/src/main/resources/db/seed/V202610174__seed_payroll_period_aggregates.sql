-- Seed: payroll period aggregates, computed from VALIDATED summaries only.
--
-- One row per (period, country, department, role, employment_type) with
-- employee_count >= 1. VALIDATED summaries are exactly the closed-period
-- summaries, so this file covers closed periods only by construction.
--
-- Amounts:
--   original_*  = SUM of the corresponding column, in the assignment currency.
--   reference_* = original amount in USD, via currency_rates.rate (foreign per
--                 1 USD). USD rows are identity (no rate row exists for USD).
--
-- Invariants, matching the CHECK constraints on payroll_period_aggregates:
--   original_total  = original_base  + original_variable
--                   + original_allowances + original_employer_taxes
--   reference_total = reference_base + reference_variable
--                   + reference_allowances + reference_employer_taxes
-- Both totals are the sum of the four individually rounded components, not
-- ROUND(sum / rate, 2), so the CHECK holds exactly (same lesson as V202610173).
--
-- status = VALIDATED on every row.
-- employee_count = number of VALIDATED summaries folded into the group.
--
-- Id derivation: ROW_NUMBER() OVER (ORDER BY <the five grouping columns>).
-- The ORDER BY covers the unique key uk_payroll_period_aggregate_group, so the
-- ordering is total and the ids are deterministic across runs, versions and
-- insertion orders. No random(), no length() hash, no fold over bytes.

WITH
grouped AS (
  SELECT
    s.payroll_period_id,
    s.assignment_country_id,
    s.assignment_department_id,
    s.assignment_role_id,
    s.assignment_employment_type,
    s.currency,
    COUNT(DISTINCT s.employee_id)          AS employee_count,
    SUM(s.base)                           AS base_sum,
    SUM(s.variable)                       AS variable_sum,
    SUM(s.allowances)                     AS allowances_sum,
    SUM(s.employer_taxes)                 AS employer_taxes_sum,
    SUM(s.base) + SUM(s.variable) + SUM(s.allowances) + SUM(s.employer_taxes)
                                          AS total_sum
  FROM payroll_summaries s
  WHERE s.status = 'VALIDATED'
  GROUP BY
    s.payroll_period_id,
    s.assignment_country_id,
    s.assignment_department_id,
    s.assignment_role_id,
    s.assignment_employment_type,
    s.currency
),
rated AS (
  SELECT
    g.payroll_period_id,
    g.assignment_country_id,
    g.assignment_department_id,
    g.assignment_role_id,
    g.assignment_employment_type,
    g.currency,
    g.employee_count,
    g.base_sum,
    g.variable_sum,
    g.allowances_sum,
    g.employer_taxes_sum,
    g.total_sum,
    cr.rate AS currency_rate
  FROM grouped g
  LEFT JOIN currency_rates cr
    ON cr.reference_currency_code = 'USD'
   AND cr.currency_code = g.currency
)
INSERT INTO payroll_period_aggregates
    (id, payroll_period_id, status,
     country_id, department_id, role_id, employment_type,
     original_base, original_variable, original_allowances,
     original_employer_taxes, original_total, original_currency,
     reference_base, reference_variable, reference_allowances,
     reference_employer_taxes, reference_total, reference_currency,
     employee_count)
SELECT
  printf('%08x-0000-4000-b000-%012x',
         ROW_NUMBER() OVER (
           ORDER BY r.payroll_period_id,
                    r.assignment_country_id,
                    r.assignment_department_id,
                    r.assignment_role_id,
                    r.assignment_employment_type),
         0),
  r.payroll_period_id,
  'VALIDATED',
  r.assignment_country_id,
  r.assignment_department_id,
  r.assignment_role_id,
  r.assignment_employment_type,
  r.base_sum,
  r.variable_sum,
  r.allowances_sum,
  r.employer_taxes_sum,
  r.total_sum,
  r.currency,
  CASE WHEN r.currency = 'USD' THEN r.base_sum
       ELSE ROUND(r.base_sum / r.currency_rate, 2) END,
  CASE WHEN r.currency = 'USD' THEN r.variable_sum
       ELSE ROUND(r.variable_sum / r.currency_rate, 2) END,
  CASE WHEN r.currency = 'USD' THEN r.allowances_sum
       ELSE ROUND(r.allowances_sum / r.currency_rate, 2) END,
  CASE WHEN r.currency = 'USD' THEN r.employer_taxes_sum
       ELSE ROUND(r.employer_taxes_sum / r.currency_rate, 2) END,
  CASE WHEN r.currency = 'USD'
       THEN r.total_sum
       ELSE ROUND(r.base_sum / r.currency_rate, 2)
          + ROUND(r.variable_sum / r.currency_rate, 2)
          + ROUND(r.allowances_sum / r.currency_rate, 2)
          + ROUND(r.employer_taxes_sum / r.currency_rate, 2)
  END,
  'USD',
  r.employee_count
FROM rated r;