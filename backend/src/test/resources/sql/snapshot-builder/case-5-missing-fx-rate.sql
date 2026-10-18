-- Case 5: one group (CM/ENG/SWE/FULL_TIME) with VALIDATED summaries in XAF, and
-- no XAF currency_rate row. Expected: 1 aggregate with originalAmounts
-- populated and referenceAmounts == null, 3 TOP + 3 BOTTOM, one MISSING_FX_RATE
-- blocker with currency XAF in details.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
('22222222-2222-2222-2222-222222222222', 'CM', 'Cameroon', 'XAF');

-- A reference currency must exist for the FX-missing path to be reached. Seed one
-- non-XAF rate so getReferenceCurrencyCode() returns "USD" and the XAF lookup then
-- fails as intended.
INSERT INTO currency_rates
  (id, currency_code, reference_currency_code, reference_amount, rate) VALUES
('77777777-7777-7777-7777-777777777778', 'EUR', 'USD', 1, 0.93);

INSERT INTO departments (id, code, name) VALUES
('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering');

INSERT INTO roles (id, code, name) VALUES
('44444444-4444-4444-4444-444444444444', 'SWE', 'Software Engineer');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
('55555555-5555-5555-5555-555555555555', '2026-01 Monthly',
 '2026-01-01', '2026-01-31', 'MONTHLY', 0);

INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
('aaaa0001-0000-0000-0000-000000000001', 'EMP-000001', 'Amadou Diallo',
 'amadou.diallo@company.com', '1990-01-01', '22222222-2222-2222-2222-222222222222'),
('aaaa0002-0000-0000-0000-000000000002', 'EMP-000002', 'Fatima Al-Hassan',
 'fatima.alhassan@company.com', '1991-02-02', '22222222-2222-2222-2222-222222222222'),
('aaaa0003-0000-0000-0000-000000000003', 'EMP-000003', 'Kwame Mensah',
 'kwame.mensah@company.com', '1992-03-03', '22222222-2222-2222-2222-222222222222');

INSERT INTO employee_periodic_assignments
  (id, employee_id, start_date, end_date, country_id, department_id, role_id,
   employment_type, frequency, in_effect) VALUES
('aaaa0011-0000-0000-0000-000000000011', 'aaaa0001-0000-0000-0000-000000000001',
 '2025-10-01', NULL, '11111111-1111-1111-1111-111111111111',
 '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444',
 'FULL_TIME', 'MONTHLY', 1),
('aaaa0012-0000-0000-0000-000000000012', 'aaaa0002-0000-0000-0000-000000000002',
 '2025-10-01', NULL, '11111111-1111-1111-1111-111111111111',
 '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444',
 'FULL_TIME', 'MONTHLY', 1),
('aaaa0013-0000-0000-0000-000000000013', 'aaaa0003-0000-0000-0000-000000000003',
 '2025-10-01', NULL, '11111111-1111-1111-1111-111111111111',
 '33333333-3333-3333-3333-333333333333', '44444444-4444-4444-4444-444444444444',
 'FULL_TIME', 'MONTHLY', 1);

INSERT INTO payroll_summaries
  (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
   assignment_country_id, assignment_department_id, assignment_role_id,
   assignment_employment_type, base, variable, allowances, employer_taxes,
   total, currency, status) VALUES
('bbbb0001-0000-0000-0000-000000000001', 'aaaa0001-0000-0000-0000-000000000001',
 '55555555-5555-5555-5555-555555555555', 'aaaa0011-0000-0000-0000-000000000011',
 '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333',
 '44444444-4444-4444-4444-444444444444', 'FULL_TIME',
 583510.00, 58351.00, 29175.50, 116702.00, 787738.50, 'XAF', 'VALIDATED'),
('bbbb0002-0000-0000-0000-000000000002', 'aaaa0002-0000-0000-0000-000000000002',
 '55555555-5555-5555-5555-555555555555', 'aaaa0012-0000-0000-0000-000000000012',
 '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333',
 '44444444-4444-4444-4444-444444444444', 'FULL_TIME',
 1167020.00, 116702.00, 58351.00, 233404.00, 1575477.00, 'XAF', 'VALIDATED'),
('bbbb0003-0000-0000-0000-000000000003', 'aaaa0003-0000-0000-0000-000000000003',
 '55555555-5555-5555-5555-555555555555', 'aaaa0013-0000-0000-0000-000000000013',
 '22222222-2222-2222-2222-222222222222', '33333333-3333-3333-3333-333333333333',
 '44444444-4444-4444-4444-444444444444', 'FULL_TIME',
 875265.00, 87526.50, 43763.25, 175053.00, 1181607.75, 'XAF', 'VALIDATED');