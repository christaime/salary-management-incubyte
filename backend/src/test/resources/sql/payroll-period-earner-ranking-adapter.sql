-- Seed for PayrollPeriodEarnerRankingRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.
--
-- Mirrors what DynamicRankingBuilder produces at snapshot build time:
--   * One ranking row per partition kind (FULL, COUNTRY, DEPARTMENT, ROLE,
--     EMPLOYMENT_TYPE, GLOBAL) per employee, per direction, per rank.
--   * One 7-point percentile distribution per partition kind with n >= 10.
--
-- Partition kinds (dimension column populated vs. the others):
--   FULL            country + department + role + employment_type
--   COUNTRY         country only
--   DEPARTMENT      department only
--   ROLE            role only
--   EMPLOYMENT_TYPE employment_type only
--   GLOBAL          none
--
-- Employees:
--   ADA   (FR, ENGINEERING, ENGINEER, FULL_TIME)
--   GRACE (DE, ENGINEERING, ENGINEER, FULL_TIME)
--
-- To keep every group at n = 2 (which the seed can represent), the
-- percentile distributions are seeded at 2. The real pipeline's
-- MIN_POPULATION_FOR_PERCENTILE = 10 guard lives in the builder, not the
-- schema — this seed just exercises read paths, so it seeds the rows anyway.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR'),
    ('22222222-2222-2222-2222-222222222222', 'DE', 'Germany', 'EUR');

INSERT INTO departments (id, code, name) VALUES
    ('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering');

INSERT INTO roles (id, code, name) VALUES
    ('44444444-4444-4444-4444-444444444444', 'SWE', 'Engineer');

INSERT INTO currency_rates
    (id, currency_code, reference_currency_code, reference_amount, rate) VALUES
    ('99999999-9999-9999-9999-999999999999', 'EUR', 'USD', 1.00, 0.93);

INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'EMP-0001', 'Ada Lovelace', 'ada@example.com',
     '1815-12-10', '11111111-1111-1111-1111-111111111111'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'EMP-0002', 'Grace Hopper', 'grace@example.com',
     '1906-12-09', '22222222-2222-2222-2222-222222222222');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
    ('55555555-5555-5555-5555-555555555555',
     'January 2026', '2026-01-01', '2026-01-31', 'MONTHLY', 0),
    ('66666666-6666-6666-6666-666666666666',
     'February 2026', '2026-02-01', '2026-02-28', 'MONTHLY', 0);

-- ===========================================================================
-- Rankings: six partition kinds per employee.
--   ADA   5000 EUR -> 5376.34 USD
--   GRACE 1200 EUR -> 1290.32 USD
-- ===========================================================================

-- FULL partition
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('77000000-0000-0000-0000-000000000001',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     5000.00, 'EUR',
     5376.34, 'USD',
     31, 5376.34, 'USD'),
    ('77000000-0000-0000-0000-000000000002',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '22222222-2222-2222-2222-222222222222',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 'BOTTOM', 1,
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     1200.00, 'EUR',
     1290.32, 'USD',
     31, 1290.32, 'USD');

-- COUNTRY partition (country only, other dimensions null)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('77000000-0000-0000-0000-000000000003',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     5000.00, 'EUR',
     5376.34, 'USD',
     31, 5376.34, 'USD'),
    ('77000000-0000-0000-0000-000000000004',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '22222222-2222-2222-2222-222222222222', NULL, NULL, NULL,
     'BOTTOM', 1,
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     1200.00, 'EUR',
     1290.32, 'USD',
     31, 1290.32, 'USD');

-- DEPARTMENT partition (department only)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('77000000-0000-0000-0000-000000000005',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     5000.00, 'EUR',
     5376.34, 'USD',
     31, 5376.34, 'USD'),
    ('77000000-0000-0000-0000-000000000006',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     'BOTTOM', 1,
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     1200.00, 'EUR',
     1290.32, 'USD',
     31, 1290.32, 'USD');

-- ROLE partition (role only)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('77000000-0000-0000-0000-000000000007',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     5000.00, 'EUR',
     5376.34, 'USD',
     31, 5376.34, 'USD'),
    ('77000000-0000-0000-0000-000000000008',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     'BOTTOM', 1,
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     1200.00, 'EUR',
     1290.32, 'USD',
     31, 1290.32, 'USD');

-- EMPLOYMENT_TYPE partition (employment_type only)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('77000000-0000-0000-0000-000000000009',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     5000.00, 'EUR',
     5376.34, 'USD',
     31, 5376.34, 'USD'),
    ('77000000-0000-0000-0000-00000000000a',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     'BOTTOM', 1,
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     1200.00, 'EUR',
     1290.32, 'USD',
     31, 1290.32, 'USD');

-- GLOBAL partition (all dimensions null)
INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
VALUES
    ('77000000-0000-0000-0000-00000000000b',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, NULL, NULL, NULL,
     'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     5000.00, 'EUR',
     5376.34, 'USD',
     31, 5376.34, 'USD'),
    ('77000000-0000-0000-0000-00000000000c',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     NULL, NULL, NULL, NULL,
     'BOTTOM', 1,
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    1200.00, 'EUR',
     1290.32, 'USD',
     31, 1290.32, 'USD');

-- ===========================================================================
-- Percentiles: 7 points per partition kind (n = 2), matching the shape
-- the builder would produce with a larger seed.
-- ===========================================================================

-- FULL partition
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile,
     reference_amount, reference_currency, employee_count)
VALUES
    ('88000000-0000-0000-0000-000000000000', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 0,   1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000000010', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 10,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000000025', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 25,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000000050', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 50,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000000075', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 75,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000000090', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 90,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000000100', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 100, 5376.34, 'USD', 2);

-- COUNTRY partition
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile,
     reference_amount, reference_currency, employee_count)
VALUES
    ('88000000-0000-0000-0000-000000001000', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     0,   1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000001010', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     10,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000001025', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     25,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000001050', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     50,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000001075', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     75,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000001090', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     90,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000001100', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     '11111111-1111-1111-1111-111111111111', NULL, NULL, NULL,
     100, 5376.34, 'USD', 2);

-- DEPARTMENT partition
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile,
     reference_amount, reference_currency, employee_count)
VALUES
    ('88000000-0000-0000-0000-000000002000', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     0,   1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000002010', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     10,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000002025', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     25,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000002050', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     50,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000002075', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     75,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000002090', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     90,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000002100', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, '33333333-3333-3333-3333-333333333333', NULL, NULL,
     100, 5376.34, 'USD', 2);

-- ROLE partition
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile,
     reference_amount, reference_currency, employee_count)
VALUES
    ('88000000-0000-0000-0000-000000003000', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     0,   1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000003010', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     10,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000003025', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     25,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000003050', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     50,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000003075', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     75,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000003090', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     90,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000003100', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, '44444444-4444-4444-4444-444444444444', NULL,
     100, 5376.34, 'USD', 2);

-- EMPLOYMENT_TYPE partition
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile,
     reference_amount, reference_currency, employee_count)
VALUES
    ('88000000-0000-0000-0000-000000004000', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     0,   1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000004010', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     10,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000004025', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     25,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000004050', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     50,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000004075', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     75,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000004090', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     90,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000004100', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, 'FULL_TIME',
     100, 5376.34, 'USD', 2);

-- GLOBAL partition
INSERT INTO payroll_period_earner_percentiles
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, percentile,
     reference_amount, reference_currency, employee_count)
VALUES
    ('88000000-0000-0000-0000-000000005000', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, NULL,
     0,   1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000005010', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, NULL,
     10,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000005025', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, NULL,
     25,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000005050', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, NULL,
     50,  1290.32, 'USD', 2),
    ('88000000-0000-0000-0000-000000005075', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, NULL,
     75,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000005090', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, NULL,
     90,  5376.34, 'USD', 2),
    ('88000000-0000-0000-0000-000000005100', '55555555-5555-5555-5555-555555555555', 'DRAFT',
     NULL, NULL, NULL, NULL,
     100, 5376.34, 'USD', 2);