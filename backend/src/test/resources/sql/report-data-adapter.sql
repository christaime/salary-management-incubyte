-- Seed for ReportDataRepositoryAdapterTest.
-- USD is the reference currency. One EUR -> USD rate exists (1.00 USD = 1.08 EUR).
--
-- Provides:
--   - 2 countries (FR, DE), 2 departments (ENG, FIN), 2 roles (ENG_1, MGR_1)
--   - 5 employees: 4 in FR (Ada, Grace, Alan, Katherine) and 1 in DE (Margaret)
--   - 1 open payroll period (January 2026)
--   - 4 FR assignments + 1 DE assignment
--   - 2 payroll_period_aggregates (FR Engineer, DE Manager) with reference
--     totals computed as the sum of the four converted components
--   - 4 VALIDATED payroll_summaries in FR (Ada, Grace, Alan, Katherine)
--     and 1 VALIDATED payroll_summary in DE (Margaret)
--   - ranking rows for every partition kind:
--       FULL             dddd...dd1..dd4   (all four dimensions set)
--       COUNTRY          dddd...dd5..dd8   (country only)
--       DEPARTMENT       dddd...dd9..dd12  (department only)
--       ROLE             dddd...dd13..dd16 (role only)
--       EMPLOYMENT_TYPE  dddd...dd17..dd18 (employment_type only)
--       GLOBAL           dddd...dd21..dd22 (no dimension)
--
-- FR (Engineering, Engineer) ranking amounts:
--   TOP    rank 1: 2700.00 EUR -> 2500.00 USD  (Grace Hopper)
--   BOTTOM rank 1: 1080.00 EUR -> 1000.00 USD  (Katherine Johnson)
-- DE (Finance, Manager) ranking amounts:
--   TOP    rank 1: 675.00 EUR -> 625.00 USD    (Margaret Hamilton)
--   BOTTOM rank 1: 675.00 EUR -> 625.00 USD    (Margaret Hamilton)
--
-- reference_total = sum of the four individually rounded converted components,
-- so the CHECK constraints on payroll_period_aggregates hold exactly.
--
-- All ranking rows below are MONTHLY over January (31 days), so
-- days_covered = 31 and monthly_rate_amount = reference_amount.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR'),
    ('22222222-2222-2222-2222-222222222222', 'DE', 'Germany', 'EUR');

INSERT INTO departments (id, code, name) VALUES
    ('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering'),
    ('44444444-4444-4444-4444-444444444444', 'FIN', 'Finance');

INSERT INTO roles (id, code, name) VALUES
    ('55555555-5555-5555-5555-555555555555', 'ENG_1', 'Engineer'),
    ('66666666-6666-6666-6666-666666666666', 'MGR_1', 'Manager');

INSERT INTO currency_rates
    (id, currency_code, reference_currency_code, reference_amount, rate)
VALUES
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'EUR', 'USD', 1.00, 1.08);

INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'EMP-0001', 'Ada Lovelace', 'ada@example.com', NULL,
     '11111111-1111-1111-1111-111111111111'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     'EMP-0002', 'Grace Hopper', 'grace@example.com', NULL,
     '11111111-1111-1111-1111-111111111111'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02',
     'EMP-0003', 'Alan Turing', 'alan@example.com', NULL,
     '11111111-1111-1111-1111-111111111111'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     'EMP-0004', 'Katherine Johnson', 'katherine@example.com', NULL,
     '11111111-1111-1111-1111-111111111111'),
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     'EMP-0005', 'Margaret Hamilton', 'margaret@example.com', NULL,
     '22222222-2222-2222-2222-222222222222');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
    ('77777777-7777-7777-7777-777777777777',
     'January 2026', '2026-01-01', '2026-01-31', 'MONTHLY', 0);

-- FR aggregate:
--   original:  1000.00 + 100.00 + 50.00 + 200.00 = 1350.00
--   reference:  925.93 +  92.59 + 46.30 + 185.19 = 1250.01
-- DE aggregate:
--   original:   500.00 +  50.00 + 25.00 + 100.00 =  675.00
--   reference:  462.96 +  46.30 + 23.15 +  92.59 =  625.00
INSERT INTO payroll_period_aggregates
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type,
     original_base, original_variable, original_allowances,
     original_employer_taxes, original_total, original_currency,
     reference_base, reference_variable, reference_allowances,
     reference_employer_taxes, reference_total, reference_currency,
     employee_count)
VALUES
    ('88888888-8888-8888-8888-888888888888',
     '77777777-7777-7777-7777-777777777777', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME',
     1000.00, 100.00, 50.00, 200.00, 1350.00, 'EUR',
     925.93, 92.59, 46.30, 185.19, 1250.01, 'USD',
     3),
    ('99999999-9999-9999-9999-999999999999',
     '77777777-7777-7777-7777-777777777777', 'DRAFT',
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'FULL_TIME',
     500.00, 50.00, 25.00, 100.00, 675.00, 'EUR',
     462.96, 46.30, 23.15, 92.59, 625.00, 'USD',
     1);

-- FR assignments.
INSERT INTO employee_periodic_assignments
    (id, employee_id, start_date, end_date, country_id, department_id, role_id,
     employment_type, frequency, in_effect)
VALUES
    ('bbbbbbbb-0000-0000-0000-000000000001',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '2025-10-01', NULL,
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'MONTHLY', 1),
    ('bbbbbbbb-0000-0000-0000-000000000002',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     '2025-10-01', NULL,
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'MONTHLY', 1),
    ('bbbbbbbb-0000-0000-0000-000000000003',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02',
     '2025-10-01', NULL,
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'MONTHLY', 1),
    ('bbbbbbbb-0000-0000-0000-000000000004',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     '2025-10-01', NULL,
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'MONTHLY', 1),
    ('bbbbbbbb-0000-0000-0000-000000000005',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     '2025-10-01', NULL,
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'FULL_TIME', 'MONTHLY', 1);

-- FR summaries: 1350, 2700, 2025, 1080 EUR.
-- DE summary: 675 EUR.
INSERT INTO payroll_summaries
    (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
     assignment_country_id, assignment_department_id, assignment_role_id,
     assignment_employment_type, base, variable, allowances, employer_taxes,
     total, currency, status)
VALUES
    ('cccccccc-0000-0000-0000-000000000001',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '77777777-7777-7777-7777-777777777777',
     'bbbbbbbb-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 1000.00, 100.00, 50.00, 200.00, 1350.00, 'EUR', 'VALIDATED'),
    ('cccccccc-0000-0000-0000-000000000002',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     '77777777-7777-7777-7777-777777777777',
     'bbbbbbbb-0000-0000-0000-000000000002',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 2000.00, 200.00, 100.00, 400.00, 2700.00, 'EUR', 'VALIDATED'),
    ('cccccccc-0000-0000-0000-000000000003',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02',
     '77777777-7777-7777-7777-777777777777',
     'bbbbbbbb-0000-0000-0000-000000000003',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 1500.00, 150.00, 75.00, 300.00, 2025.00, 'EUR', 'VALIDATED'),
    ('cccccccc-0000-0000-0000-000000000004',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     '77777777-7777-7777-7777-777777777777',
     'bbbbbbbb-0000-0000-0000-000000000004',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 800.00, 80.00, 40.00, 160.00, 1080.00, 'EUR', 'VALIDATED'),
    ('cccccccc-0000-0000-0000-000000000005',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     '77777777-7777-7777-7777-777777777777',
     'bbbbbbbb-0000-0000-0000-000000000005',
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'FULL_TIME', 500.00, 50.00, 25.00, 100.00, 675.00, 'EUR', 'VALIDATED');

-- ===========================================================================
-- Ranking rows, one block per partition kind.
-- FR rows use Grace (TOP) and Katherine (BOTTOM).
-- DE rows use Margaret (both directions, only one employee in DE).
-- ===========================================================================

-- FULL partition: country_id, department_id, role_id, employment_type all set.
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd1',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     2700.00, 'EUR', 2500.00, 'USD',
     31, 2500.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd2',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     1080.00, 'EUR', 1000.00, 'USD',
     31, 1000.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd3',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'FULL_TIME', 'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd4',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'FULL_TIME', 'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD');

-- COUNTRY partition: country_id set, others NULL.
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd5',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     2700.00, 'EUR', 2500.00, 'USD',
     31, 2500.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd6',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     1080.00, 'EUR', 1000.00, 'USD',
     31, 1000.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd7',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '22222222-2222-2222-2222-222222222222', NULL, NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd8',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '22222222-2222-2222-2222-222222222222', NULL, NULL, NULL,
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD');

-- DEPARTMENT partition: department_id set, others NULL.
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd9',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     2700.00, 'EUR', 2500.00, 'USD',
     31, 2500.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd10',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     1080.00, 'EUR', 1000.00, 'USD',
     31, 1000.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd11',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, '44444444-4444-4444-4444-444444444444', NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd12',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, '44444444-4444-4444-4444-444444444444', NULL, NULL,
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD');

-- ROLE partition: role_id set, others NULL.
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd13',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, '55555555-5555-5555-5555-555555555555', NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     2700.00, 'EUR', 2500.00, 'USD',
     31, 2500.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd14',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, '55555555-5555-5555-5555-555555555555', NULL,
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     1080.00, 'EUR', 1000.00, 'USD',
     31, 1000.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd15',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, '66666666-6666-6666-6666-666666666666', NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd16',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, '66666666-6666-6666-6666-666666666666', NULL,
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD');

-- EMPLOYMENT_TYPE partition: employment_type set, others NULL.
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd17',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, NULL, 'FULL_TIME',
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     2700.00, 'EUR', 2500.00, 'USD',
     31, 2500.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd18',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, NULL, 'FULL_TIME',
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD');

-- GLOBAL partition: all four dimensions NULL.
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd21',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     2700.00, 'EUR', 2500.00, 'USD',
     31, 2500.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd22',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     NULL, NULL, NULL, NULL,
     'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04',
     675.00, 'EUR', 625.00, 'USD',
     31, 625.00, 'USD');