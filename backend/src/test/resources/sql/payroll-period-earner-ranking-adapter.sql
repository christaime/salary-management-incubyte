-- Seed for PayrollPeriodEarnerRankingRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.
-- Each ranking row carries the original amount (assignment currency) and the
-- reference amount (USD). Conversion is total * reference_amount / rate.

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

INSERT INTO payroll_period_earner_rankings
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type, direction, rank, employee_id,
     original_amount, original_currency,
     reference_amount, reference_currency)
VALUES
    ('77777777-7777-7777-7777-777777777777',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 'TOP', 1,
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     5000.00, 'EUR',
     5376.34, 'USD'),
    ('88888888-8888-8888-8888-888888888888',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '22222222-2222-2222-2222-222222222222',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME', 'BOTTOM', 1,
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     1200.00, 'EUR',
     1290.32, 'USD');