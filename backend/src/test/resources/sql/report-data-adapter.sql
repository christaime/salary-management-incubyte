-- Seed for ReportDataRepositoryAdapterTest.
-- USD is the reference currency. One EUR -> USD rate exists.
--
-- The seed provides:
--   - 2 countries (FR, DE), 2 departments (ENG, FIN), 2 roles (ENG_1, MGR_1)
--   - 1 EUR -> USD rate: reference_amount = 1.00, rate = 1.08
--     Reads as "1.00 USD = 1.08 EUR", so 1 EUR = 1/1.08 USD. To convert an
--     amount in EUR to USD, divide by 1.08.
--   - 4 employees
--   - 1 payroll period (January 2026, open)
--   - 2 payroll_period_aggregates (FR Engineer, DE Manager) so the aggregate
--     tests have data
--   - 4 employee_periodic_assignments (FK targets for the summaries)
--   - 4 VALIDATED payroll_summaries in the FR group
--   - 4 payroll_period_earner_rankings (FR TOP/BOTTOM rank 1, DE TOP/BOTTOM
--     rank 1) so queryStatistics has a ranking population to read
--
-- Reference amounts are computed with division by the rate, consistent with
-- the JPQL used by queryAggregates, queryStatistics, topByGroup, and
-- bottomByGroup (CASE ... ELSE original * reference_amount / rate ... END).
--
-- reference_total (and original_total) is the sum of the four rounded
-- components, not the single rounded original_total / rate. The
-- ck_aggregate_reference_total and ck_aggregate_original_total CHECK
-- constraints require this, and Amounts.total is computed the same way.
-- For FR that means 925.93 + 92.59 + 46.30 + 185.19 = 1250.01, not 1250.00.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR'),
    ('22222222-2222-2222-2222-222222222222', 'DE', 'Germany', 'EUR');

INSERT INTO departments (id, code, name) VALUES
    ('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering'),
    ('44444444-4444-4444-4444-444444444444', 'FIN', 'Finance');

INSERT INTO roles (id, code, name) VALUES
    ('55555555-5555-5555-5555-555555555555', 'ENG_1', 'Engineer'),
    ('66666666-6666-6666-6666-666666666666', 'MGR_1', 'Manager');

-- 1.00 USD = 1.08 EUR. EUR -> USD divides by 1.08.
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
     '11111111-1111-1111-1111-111111111111');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
    ('77777777-7777-7777-7777-777777777777',
     'January 2026', '2026-01-01', '2026-01-31', 'MONTHLY', 0);

-- FR aggregate.
--   original:  1000.00 + 100.00 + 50.00 + 200.00 = 1350.00
--   reference: 1000.00/1.08 = 925.93
--               100.00/1.08 =  92.59
--                50.00/1.08 =  46.30
--               200.00/1.08 = 185.19
--               sum          = 1250.01
-- DE aggregate.
--   original:   500.00 +  50.00 + 25.00 + 100.00 =  675.00
--   reference:  500.00/1.08 = 462.96
--                50.00/1.08 =  46.30
--                25.00/1.08 =  23.15
--               100.00/1.08 =  92.59
--               sum         = 625.00
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

-- Assignments, FK targets for the summaries.
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
     'FULL_TIME', 'MONTHLY', 1);

-- Four VALIDATED FR summaries. Totals in EUR: 1350, 2700, 2025, 1080.
-- In USD, after dividing by 1.08:
--   1350.00 / 1.08 = 1250.00
--   2700.00 / 1.08 = 2500.00
--   2025.00 / 1.08 = 1875.00
--   1080.00 / 1.08 = 1000.00
-- min = 1000.00, max = 2500.00.
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
     'FULL_TIME', 800.00, 80.00, 40.00, 160.00, 1080.00, 'EUR', 'VALIDATED');

-- Four rank-1 ranking rows, one TOP and one BOTTOM per group, so
-- queryStatistics has a population to read.
--
-- FR (Engineering, Engineer):
--   TOP    rank 1: 2700.00 EUR -> 2500.00 USD
--   BOTTOM rank 1: 1080.00 EUR -> 1000.00 USD
--
-- DE (Finance, Manager):
--   TOP    rank 1: 675.00 EUR -> 625.00 USD
--   BOTTOM rank 1: 675.00 EUR -> 625.00 USD
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency)
VALUES
    ('dddddddd-dddd-dddd-dddd-ddddddddddd1',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01',
     2700.00, 'EUR',
     2500.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd2',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03',
     1080.00, 'EUR',
     1000.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd3',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'FULL_TIME', 'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     675.00, 'EUR',
     625.00, 'USD'),
    ('dddddddd-dddd-dddd-dddd-ddddddddddd4',
     '77777777-7777-7777-7777-777777777777', 'VALIDATED',
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'FULL_TIME', 'BOTTOM', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     675.00, 'EUR',
     625.00, 'USD');