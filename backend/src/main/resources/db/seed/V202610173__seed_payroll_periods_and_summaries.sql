-- Seed: payroll periods and per-employee summaries.
--
-- 19 periods: 6 monthly (3 closed, 3 open) and 13 bi-weekly (7 closed, 6 open),
-- all starting 2026-01-01. Monthly period k covers the k-th calendar month of
-- 2026; bi-weekly period k covers days 14(k-1)+1 .. 14k from 2026-01-01. The
-- two cadences overlap by design. The last period ends 2026-07-01.
--
-- Summaries: one per (employee, period) where the employee's assignment is in
-- effect for the period AND the employment type matches the period cadence:
--   FULL_TIME            -> MONTHLY periods only
--   PART_TIME, CONTRACTOR-> BI_WEEKLY periods only (CONTRACTOR is not seeded)
-- In effect: in_effect = 1 AND start_date <= period.end_date
--            AND (end_date IS NULL OR end_date >= period.start_date).
--
-- With the 10% of assignments ending 2026-05-18 (see V202610172):
--   FULL_TIME : months 1-5 for the 10%, months 1-6 for the rest.
--   PART_TIME : B01-B10 for the 10%, B01-B13 for the rest.
-- Closed periods (monthly 1-3, bi-weekly 1-7) are covered by every group, so
-- the VALIDATED slice is unaffected by the assignment-end rule.
--
-- Expected row counts (approximate, from the 6/7 FULL_TIME split):
--   FULL_TIME total      ~50,569   VALIDATED ~25,713
--   PART_TIME total      ~18,148   VALIDATED ~10,003
--   All       total      ~68,717   VALIDATED ~35,716
--
-- Amounts are in the assignment country's currency. total = base + variable +
-- allowances + employer_taxes, always.
--
-- Base salary per role is from the salary table below (annual, USD). Each
-- employee's annual value is picked inside [annual_min, annual_max] by a
-- deterministic hash of n; it is divided by 12 (monthly) or 26 (bi-weekly),
-- scaled by 50% for PART_TIME, then converted to the assignment currency via
-- currency_rates.rate (foreign units per 1 USD). SALES commission is folded
-- into the range, not added separately.
--
-- Status: VALIDATED on closed periods, DRAFT on open periods.
-- No random(), no now(): every amount is a function of n, period index and role.

-- ---------------------------------------------------------------------------
-- payroll_periods (19 rows)
-- ---------------------------------------------------------------------------
INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
('f0000001-0000-4000-8000-000000000001', '2026-01 Monthly',    '2026-01-01', '2026-01-31', 'MONTHLY',   1),
('f0000001-0000-4000-8000-000000000002', '2026-02 Monthly',    '2026-02-01', '2026-02-28', 'MONTHLY',   1),
('f0000001-0000-4000-8000-000000000003', '2026-03 Monthly',    '2026-03-01', '2026-03-31', 'MONTHLY',   1),
('f0000001-0000-4000-8000-000000000004', '2026-04 Monthly',    '2026-04-01', '2026-04-30', 'MONTHLY',   0),
('f0000001-0000-4000-8000-000000000005', '2026-05 Monthly',    '2026-05-01', '2026-05-31', 'MONTHLY',   0),
('f0000001-0000-4000-8000-000000000006', '2026-06 Monthly',    '2026-06-01', '2026-06-30', 'MONTHLY',   0),
('f0000002-0000-4000-8000-000000000001', '2026-B01 Bi-weekly', '2026-01-01', '2026-01-14', 'BI_WEEKLY', 1),
('f0000002-0000-4000-8000-000000000002', '2026-B02 Bi-weekly', '2026-01-15', '2026-01-28', 'BI_WEEKLY', 1),
('f0000002-0000-4000-8000-000000000003', '2026-B03 Bi-weekly', '2026-01-29', '2026-02-11', 'BI_WEEKLY', 1),
('f0000002-0000-4000-8000-000000000004', '2026-B04 Bi-weekly', '2026-02-12', '2026-02-25', 'BI_WEEKLY', 1),
('f0000002-0000-4000-8000-000000000005', '2026-B05 Bi-weekly', '2026-02-26', '2026-03-11', 'BI_WEEKLY', 1),
('f0000002-0000-4000-8000-000000000006', '2026-B06 Bi-weekly', '2026-03-12', '2026-03-25', 'BI_WEEKLY', 1),
('f0000002-0000-4000-8000-000000000007', '2026-B07 Bi-weekly', '2026-03-26', '2026-04-08', 'BI_WEEKLY', 1),
('f0000002-0000-4000-8000-000000000008', '2026-B08 Bi-weekly', '2026-04-09', '2026-04-22', 'BI_WEEKLY', 0),
('f0000002-0000-4000-8000-000000000009', '2026-B09 Bi-weekly', '2026-04-23', '2026-05-06', 'BI_WEEKLY', 0),
('f0000002-0000-4000-8000-00000000000a', '2026-B10 Bi-weekly', '2026-05-07', '2026-05-20', 'BI_WEEKLY', 0),
('f0000002-0000-4000-8000-00000000000b', '2026-B11 Bi-weekly', '2026-05-21', '2026-06-03', 'BI_WEEKLY', 0),
('f0000002-0000-4000-8000-00000000000c', '2026-B12 Bi-weekly', '2026-06-04', '2026-06-17', 'BI_WEEKLY', 0),
('f0000002-0000-4000-8000-00000000000d', '2026-B13 Bi-weekly', '2026-06-18', '2026-07-01', 'BI_WEEKLY', 0);

-- ---------------------------------------------------------------------------
-- payroll_summaries
--
-- Nine narrow CTEs, each doing one transformation, one cross-join only.
-- salary band from the role table below (annual, USD).
-- hash(n) = (n * 2654435761) % (annual_max - annual_min + 1) spreads n inside
-- the band. Not a perfect uniform, but deterministic and well-distributed.
-- ---------------------------------------------------------------------------
WITH RECURSIVE
-- 19 periods with literal dates. No date arithmetic.
periods(p_idx, p_id, frequency, closed, start_date, end_date) AS (
  VALUES
  ( 1, 'f0000001-0000-4000-8000-000000000001', 'MONTHLY',   1, '2026-01-01', '2026-01-31'),
  ( 2, 'f0000001-0000-4000-8000-000000000002', 'MONTHLY',   1, '2026-02-01', '2026-02-28'),
  ( 3, 'f0000001-0000-4000-8000-000000000003', 'MONTHLY',   1, '2026-03-01', '2026-03-31'),
  ( 4, 'f0000001-0000-4000-8000-000000000004', 'MONTHLY',   0, '2026-04-01', '2026-04-30'),
  ( 5, 'f0000001-0000-4000-8000-000000000005', 'MONTHLY',   0, '2026-05-01', '2026-05-31'),
  ( 6, 'f0000001-0000-4000-8000-000000000006', 'MONTHLY',   0, '2026-06-01', '2026-06-30'),
  ( 7, 'f0000002-0000-4000-8000-000000000001', 'BI_WEEKLY', 1, '2026-01-01', '2026-01-14'),
  ( 8, 'f0000002-0000-4000-8000-000000000002', 'BI_WEEKLY', 1, '2026-01-15', '2026-01-28'),
  ( 9, 'f0000002-0000-4000-8000-000000000003', 'BI_WEEKLY', 1, '2026-01-29', '2026-02-11'),
  (10, 'f0000002-0000-4000-8000-000000000004', 'BI_WEEKLY', 1, '2026-02-12', '2026-02-25'),
  (11, 'f0000002-0000-4000-8000-000000000005', 'BI_WEEKLY', 1, '2026-02-26', '2026-03-11'),
  (12, 'f0000002-0000-4000-8000-000000000006', 'BI_WEEKLY', 1, '2026-03-12', '2026-03-25'),
  (13, 'f0000002-0000-4000-8000-000000000007', 'BI_WEEKLY', 1, '2026-03-26', '2026-04-08'),
  (14, 'f0000002-0000-4000-8000-000000000008', 'BI_WEEKLY', 0, '2026-04-09', '2026-04-22'),
  (15, 'f0000002-0000-4000-8000-000000000009', 'BI_WEEKLY', 0, '2026-04-23', '2026-05-06'),
  (16, 'f0000002-0000-4000-8000-00000000000a', 'BI_WEEKLY', 0, '2026-05-07', '2026-05-20'),
  (17, 'f0000002-0000-4000-8000-00000000000b', 'BI_WEEKLY', 0, '2026-05-21', '2026-06-03'),
  (18, 'f0000002-0000-4000-8000-00000000000c', 'BI_WEEKLY', 0, '2026-06-04', '2026-06-17'),
  (19, 'f0000002-0000-4000-8000-00000000000d', 'BI_WEEKLY', 0, '2026-06-18', '2026-07-01')
),
-- Salary band per role code (annual, USD). pt_factor is 0.5 for PART_TIME.
role_band(role_code, annual_min, annual_max, pt_factor) AS (
  VALUES
  ('PROD',    65000, 130000, 0.5),
  ('SPROD',  110000, 195000, 0.5),
  ('EDIT',    45000, 120000, 0.5),
  ('ENG',     80000, 140000, 0.5),
  ('SENG',   130000, 210000, 0.5),
  ('A&R',     60000, 135000, 0.5),
  ('EVENT',   45000,  85000, 0.5),
  ('MKTG',    70000, 140000, 0.5),
  ('SALES',   60000, 130000, 0.5),
  ('DISTRIB', 65000, 120000, 0.5),
  ('FIN',     70000, 135000, 0.5),
  ('LEGAL',   90000, 180000, 0.5)
),
-- Employees with their assignment, role code, currency and cadence.
ew(year_n, employee_id, assignment_id, country_id, department_id, role_id,
   employment_type, role_code, currency, cadence, assignment_start, assignment_end) AS (
  SELECT
    CAST(substr(e.matricule, 5) AS INTEGER),
    e.id,
    a.id,
    a.country_id,
    a.department_id,
    a.role_id,
    a.employment_type,
    r.code,
    c.currency_code,
    CASE a.employment_type
      WHEN 'FULL_TIME'             THEN 'MONTHLY'
      WHEN 'PART_TIME'             THEN 'BI_WEEKLY'
      WHEN 'CONTRACTOR'            THEN 'BI_WEEKLY'
    END,
    a.start_date,
    a.end_date
  FROM employees e
  JOIN employee_periodic_assignments a
    ON a.employee_id = e.id
   AND a.in_effect = 1
  JOIN roles r     ON r.id = a.role_id
  JOIN countries c ON c.id = a.country_id
),
-- Cross-join ew × periods on cadence and overlap.
pairs(year_n, p_idx, p_id, frequency, closed, employee_id, assignment_id,
      country_id, department_id, role_id, employment_type, role_code,
      currency) AS (
  SELECT e.year_n, p.p_idx, p.p_id, p.frequency, p.closed,
         e.employee_id, e.assignment_id, e.country_id, e.department_id,
         e.role_id, e.employment_type, e.role_code, e.currency
  FROM ew e
  JOIN periods p
    ON p.frequency = e.cadence
   AND e.assignment_start <= p.end_date
   AND (e.assignment_end IS NULL OR e.assignment_end >= p.start_date)
),
-- Pick annual salary inside the band by hash(n).
picked(year_n, p_idx, p_id, frequency, closed, employee_id, assignment_id,
       country_id, department_id, role_id, employment_type, role_code,
       currency, annual) AS (
  SELECT pr.year_n, pr.p_idx, pr.p_id, pr.frequency, pr.closed,
         pr.employee_id, pr.assignment_id, pr.country_id, pr.department_id,
         pr.role_id, pr.employment_type, pr.role_code, pr.currency,
         rb.annual_min
           + CAST((pr.year_n * 2654435761) AS INTEGER)
             % (rb.annual_max - rb.annual_min + 1)
  FROM pairs pr
  JOIN role_band rb ON rb.role_code = pr.role_code
),
-- Divide to per-period USD, apply PART_TIME factor.
divided(year_n, p_idx, p_id, frequency, closed, employee_id, assignment_id,
        country_id, department_id, role_id, employment_type, role_code,
        currency, per_period_usd) AS (
  SELECT pk.year_n, pk.p_idx, pk.p_id, pk.frequency, pk.closed,
         pk.employee_id, pk.assignment_id, pk.country_id, pk.department_id,
         pk.role_id, pk.employment_type, pk.role_code, pk.currency,
         ROUND(
           pk.annual
             / (CASE pk.frequency WHEN 'MONTHLY' THEN 12.0 ELSE 26.0 END)
             * (CASE pk.employment_type WHEN 'PART_TIME' THEN 0.5 ELSE 1.0 END),
           2)
  FROM picked pk
),
-- Convert USD amount to the assignment country's currency.
converted(year_n, p_idx, p_id, frequency, closed, employee_id, assignment_id,
          country_id, department_id, role_id, employment_type, role_code,
          currency, base) AS (
  SELECT dv.year_n, dv.p_idx, dv.p_id, dv.frequency, dv.closed,
         dv.employee_id, dv.assignment_id, dv.country_id, dv.department_id,
         dv.role_id, dv.employment_type, dv.role_code, dv.currency,
         CASE dv.currency
           WHEN 'USD' THEN dv.per_period_usd
           ELSE ROUND(dv.per_period_usd * cr.rate, 2)
         END
  FROM divided dv
  LEFT JOIN currency_rates cr
    ON cr.reference_currency_code = 'USD'
   AND cr.currency_code = dv.currency
),

-- Add variable, allowances, employer_taxes, total. All percentages of base.
-- Each component is rounded to 2 decimals BEFORE summing, so the stored
-- total equals base + variable + allowances + employer_taxes exactly.
amounted(year_n, p_idx, p_id, frequency, closed, employee_id, assignment_id,
         country_id, department_id, role_id, employment_type, currency,
         base, variable, allowances, employer_taxes, total) AS (
  SELECT cv.year_n, cv.p_idx, cv.p_id, cv.frequency, cv.closed,
         cv.employee_id, cv.assignment_id, cv.country_id, cv.department_id,
         cv.role_id, cv.employment_type, cv.currency,
         cv.base,
         ROUND(cv.base * (5  + (cv.year_n + cv.p_idx) % 16) / 100.0, 2),
         ROUND(cv.base * (2  + (cv.year_n + cv.p_idx) %  9) / 100.0, 2),
         ROUND(cv.base * (10 + (cv.year_n + cv.p_idx) % 11) / 100.0, 2),
         cv.base
         + ROUND(cv.base * (5  + (cv.year_n + cv.p_idx) % 16) / 100.0, 2)
         + ROUND(cv.base * (2  + (cv.year_n + cv.p_idx) %  9) / 100.0, 2)
         + ROUND(cv.base * (10 + (cv.year_n + cv.p_idx) % 11) / 100.0, 2)
  FROM converted cv
)

INSERT INTO payroll_summaries
    (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
     assignment_country_id, assignment_department_id, assignment_role_id,
     assignment_employment_type, base, variable, allowances, employer_taxes,
     total, currency, status)
SELECT
  printf('%08x-0000-4000-a000-%012x', a.year_n, a.p_idx),
  a.employee_id,
  a.p_id,
  a.assignment_id,
  a.country_id,
  a.department_id,
  a.role_id,
  a.employment_type,
  a.base,
  a.variable,
  a.allowances,
  a.employer_taxes,
  a.total,
  a.currency,
  CASE a.closed WHEN 1 THEN 'VALIDATED' ELSE 'DRAFT' END
FROM amounted a;