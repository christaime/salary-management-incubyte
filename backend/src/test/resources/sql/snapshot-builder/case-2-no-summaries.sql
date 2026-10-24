-- Case 2: open period, three employees with in-effect assignments covering the
-- period, zero payroll summaries. Builder returns MISSING_PAYROLL_SUMMARY with
-- the first 10 employee ids (here all 3) and produces no aggregates/rankings.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
('11111111-1111-1111-1111-111111111111', 'US', 'United States', 'USD');

INSERT INTO departments (id, code, name) VALUES
('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering');

INSERT INTO roles (id, code, name) VALUES
('44444444-4444-4444-4444-444444444444', 'SWE', 'Software Engineer');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
('55555555-5555-5555-5555-555555555555', '2026-01 Monthly',
 '2026-01-01', '2026-01-31', 'MONTHLY', 0);

INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
('aaaa0001-0000-0000-0000-000000000001', 'EMP-000001', 'Alice Smith',
 'alice.smith@company.com', '1990-01-01', '11111111-1111-1111-1111-111111111111'),
('aaaa0002-0000-0000-0000-000000000002', 'EMP-000002', 'Bob Jones',
 'bob.jones@company.com', '1991-02-02', '11111111-1111-1111-1111-111111111111'),
('aaaa0003-0000-0000-0000-000000000003', 'EMP-000003', 'Carol White',
 'carol.white@company.com', '1992-03-03', '11111111-1111-1111-1111-111111111111');

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