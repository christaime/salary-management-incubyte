-- src/test/resources/sql/payroll-period-snapshot.sql
--
-- Deterministic dataset for PayrollPeriodSnapshotRepositoryAdapterTest.
-- Two countries (FR/DE), two departments (ENG/FIN), one role each, one
-- employment type each. Two periods (January and February 2026), both
-- MONTHLY. Amounts chosen so the aggregates have clean round numbers
-- after conversion to USD.
--
-- Reference currency: USD. Rate: 1 EUR = 1.10 USD (so EUR amounts × 1.1
-- give clean USD values). 1 USD = 1 USD (self-pair, identity).

-- Cleanup (safe: @Transactional rolls back anyway, but this makes the
-- fixture re-runnable outside a transaction if someone wants to).
DELETE FROM payroll_period_earner_percentiles;
DELETE FROM payroll_period_earner_rankings;
DELETE FROM payroll_period_aggregates;
DELETE FROM payroll_summaries;
DELETE FROM employee_periodic_assignments;
DELETE FROM payroll_periods;
DELETE FROM employees;
DELETE FROM roles;
DELETE FROM departments;
DELETE FROM countries;
DELETE FROM currency_rates;

-- ---------------------------------------------------------------------------
-- Reference data
-- ---------------------------------------------------------------------------
INSERT INTO countries (id, iso_code, name, currency_code) VALUES
  ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR'),
  ('22222222-2222-2222-2222-222222222222', 'DE', 'Germany', 'EUR');

INSERT INTO departments (id, code, name) VALUES
  ('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering'),
  ('44444444-4444-4444-4444-444444444444', 'FIN', 'Finance');

INSERT INTO roles (id, code, name) VALUES
  ('55555555-5555-5555-5555-555555555555', 'ENGINEER', 'Engineer'),
  ('66666666-6666-6666-6666-666666666666', 'MANAGER',  'Manager');

INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'EMP001', 'Alice Martin', 'alice@example.com',  '1990-01-15', '11111111-1111-1111-1111-111111111111'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'EMP002', 'Bob Durand',   'bob@example.com',    '1985-03-22', '11111111-1111-1111-1111-111111111111'),
  ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'EMP003', 'Clara Klein',  'clara@example.com',  '1992-07-09', '22222222-2222-2222-2222-222222222222'),
  ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'EMP004', 'Dieter Müller','dieter@example.com', '1988-11-30', '22222222-2222-2222-2222-222222222222');

-- Reference currency = USD. One rate row (all rows agree on USD, per the
-- single-reference trigger). EUR -> USD: 1 EUR = 1.10 USD.
-- (reference_amount, reference_currency, rate, currency)
--   1 USD = 1 USD (self-pair, identity)
--   1 USD = 0.909090… EUR is *not* how the table works; the row is
--   "1 USD = 0.909 EUR" written as (1, USD, 0.909, EUR). To get "1 EUR
--   = 1.10 USD" we use the row (1, USD, 1.10, EUR), meaning
--   "1 USD = 1.10 EUR". Conversion in SQL is amount_eur * reference_amount
--   / rate  =>  amount_eur * 1 / 1.10  =  amount_eur / 1.10.
-- We want to multiply by 1.10, so we use rate = 0.909090… But that's not
-- clean. Instead, use reference_amount = 1.10, rate = 1.0:
--   "1.10 USD = 1 EUR"  =>  amount_eur * 1.10 / 1.0  =  amount_eur * 1.10.
-- That gives the clean factor.
INSERT INTO currency_rates (id, currency_code, reference_currency_code, reference_amount, rate) VALUES
  ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'USD', 'USD', 1.00, 1.00),
  ('ffffffff-ffff-ffff-ffff-ffffffffffff', 'EUR', 'USD', 1.10, 1.00);

-- ---------------------------------------------------------------------------
-- Payroll periods — January and February 2026, both MONTHLY
-- ---------------------------------------------------------------------------
INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
  ('77777777-7777-7777-7777-777777777777', 'January 2026',  '2026-01-01', '2026-01-31', 'MONTHLY', FALSE),
  ('88888888-8888-8888-8888-888888888888', 'February 2026', '2026-02-01', '2026-02-28', 'MONTHLY', FALSE);

-- ---------------------------------------------------------------------------
-- Assignments — one per employee, spanning the whole test range
-- ---------------------------------------------------------------------------
INSERT INTO employee_periodic_assignments
  (id, employee_id, start_date, end_date, country_id, department_id, role_id,
   employment_type, frequency, in_effect) VALUES
  ('99999999-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
   '2025-01-01', NULL,
   '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333',
   '55555555-5555-5555-5555-555555555555', 'FULL_TIME', 'MONTHLY', TRUE),
  ('99999999-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
   '2025-01-01', NULL,
   '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333',
   '55555555-5555-5555-5555-555555555555', 'FULL_TIME', 'MONTHLY', TRUE),
  ('99999999-0000-0000-0000-000000000003', 'cccccccc-cccc-cccc-cccc-cccccccccccc',
   '2025-01-01', NULL,
   '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444',
   '66666666-6666-6666-6666-666666666666', 'FULL_TIME', 'MONTHLY', TRUE),
  ('99999999-0000-0000-0000-000000000004', 'dddddddd-dddd-dddd-dddd-dddddddddddd',
   '2025-01-01', NULL,
   '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444',
   '66666666-6666-6666-6666-666666666666', 'FULL_TIME', 'MONTHLY', TRUE);

-- ---------------------------------------------------------------------------
-- Summaries — January
--
-- Totals after conversion to USD:
--   Alice  (FR/ENG/Engineer, EUR 1000)  -> USD 1100
--   Bob    (FR/ENG/Engineer, EUR 2000)  -> USD 2200
--   Clara  (DE/FIN/Manager,  EUR  500)  -> USD  550
--   Dieter (DE/FIN/Manager,  EUR  500)  -> USD  550
--
-- Aggregates by (country, department, role, employment_type):
--   (FR, ENG, Engineer, FULL_TIME): count=2, original_total=EUR 3000, reference_total=USD 3300
--   (DE, FIN, Manager,  FULL_TIME): count=2, original_total=EUR 1000, reference_total=USD 1100
--
-- MIN/MAX by COUNTRY (monthly, USD):
--   FR: min=1100, max=2200
--   DE: min=550,  max=550
-- ---------------------------------------------------------------------------
INSERT INTO payroll_summaries
  (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
   assignment_country_id, assignment_department_id, assignment_role_id,
   assignment_employment_type, base, variable, allowances, employer_taxes,
   total, currency, status) VALUES
  ('aaaa0001-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
   '77777777-7777-7777-7777-777777777777', '99999999-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333',
   '55555555-5555-5555-5555-555555555555', 'FULL_TIME',
   800.00, 100.00, 50.00, 50.00, 1000.00, 'EUR', 'VALIDATED'),
  ('aaaa0001-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
   '77777777-7777-7777-7777-777777777777', '99999999-0000-0000-0000-000000000002',
   '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333',
   '55555555-5555-5555-5555-555555555555', 'FULL_TIME',
   1600.00, 200.00, 100.00, 100.00, 2000.00, 'EUR', 'VALIDATED'),
  ('aaaa0001-0000-0000-0000-000000000003', 'cccccccc-cccc-cccc-cccc-cccccccccccc',
   '77777777-7777-7777-7777-777777777777', '99999999-0000-0000-0000-000000000003',
   '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444',
   '66666666-6666-6666-6666-666666666666', 'FULL_TIME',
   400.00, 50.00, 25.00, 25.00, 500.00, 'EUR', 'VALIDATED'),
  ('aaaa0001-0000-0000-0000-000000000004', 'dddddddd-dddd-dddd-dddd-dddddddddddd',
   '77777777-7777-7777-7777-777777777777', '99999999-0000-0000-0000-000000000004',
   '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444',
   '66666666-6666-6666-6666-666666666666', 'FULL_TIME',
   400.00, 50.00, 25.00, 25.00, 500.00, 'EUR', 'VALIDATED');

-- ---------------------------------------------------------------------------
-- Summaries — February (same shape, amounts scaled so we can prove per-period)
--
-- Totals after conversion to USD:
--   Alice  EUR 1000 -> USD 1100
--   Bob    EUR 1500 -> USD 1650
--   Clara  EUR  500 -> USD  550
--   Dieter EUR  500 -> USD  550
-- ---------------------------------------------------------------------------
INSERT INTO payroll_summaries
  (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
   assignment_country_id, assignment_department_id, assignment_role_id,
   assignment_employment_type, base, variable, allowances, employer_taxes,
   total, currency, status) VALUES
  ('aaaa0002-0000-0000-0000-000000000001', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
   '88888888-8888-8888-8888-888888888888', '99999999-0000-0000-0000-000000000001',
   '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333',
   '55555555-5555-5555-5555-555555555555', 'FULL_TIME',
   800.00, 100.00, 50.00, 50.00, 1000.00, 'EUR', 'VALIDATED'),
  ('aaaa0002-0000-0000-0000-000000000002', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
   '88888888-8888-8888-8888-888888888888', '99999999-0000-0000-0000-000000000002',
   '11111111-1111-1111-1111-111111111111', '33333333-3333-3333-3333-333333333333',
   '55555555-5555-5555-5555-555555555555', 'FULL_TIME',
   1200.00, 150.00, 75.00, 75.00, 1500.00, 'EUR', 'VALIDATED'),
  ('aaaa0002-0000-0000-0000-000000000003', 'cccccccc-cccc-cccc-cccc-cccccccccccc',
   '88888888-8888-8888-8888-888888888888', '99999999-0000-0000-0000-000000000003',
   '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444',
   '66666666-6666-6666-6666-666666666666', 'FULL_TIME',
   400.00, 50.00, 25.00, 25.00, 500.00, 'EUR', 'VALIDATED'),
  ('aaaa0002-0000-0000-0000-000000000004', 'dddddddd-dddd-dddd-dddd-dddddddddddd',
   '88888888-8888-8888-8888-888888888888', '99999999-0000-0000-0000-000000000004',
   '22222222-2222-2222-2222-222222222222', '44444444-4444-4444-4444-444444444444',
   '66666666-6666-6666-6666-666666666666', 'FULL_TIME',
   400.00, 50.00, 25.00, 25.00, 500.00, 'EUR', 'VALIDATED');