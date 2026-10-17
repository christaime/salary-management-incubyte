-- Seed for PayrollPeriodAggregateRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR'),
    ('22222222-2222-2222-2222-222222222222', 'DE', 'Germany', 'EUR');

INSERT INTO departments (id, code, name) VALUES
    ('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering');

INSERT INTO roles (id, code, name) VALUES
    ('44444444-4444-4444-4444-444444444444', 'SWE', 'Engineer');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
    ('55555555-5555-5555-5555-555555555555',
     'January 2026', '2026-01-01', '2026-01-31', 'MONTHLY', 0),
    ('66666666-6666-6666-6666-666666666666',
     'February 2026', '2026-02-01', '2026-02-28', 'MONTHLY', 0);

INSERT INTO payroll_period_aggregates
    (id, payroll_period_id, status, country_id, department_id, role_id,
     employment_type,
     original_base, original_variable, original_allowances,
     original_employer_taxes, original_total, original_currency,
     reference_base, reference_variable, reference_allowances,
     reference_employer_taxes, reference_total, reference_currency,
     employee_count)
VALUES
    ('77777777-7777-7777-7777-777777777777',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'FULL_TIME',
     1000.00, 100.00, 50.00, 200.00, 1350.00, 'EUR',
     1080.00, 108.00, 54.00, 216.00, 1458.00, 'EUR',
     3),
    ('88888888-8888-8888-8888-888888888888',
     '55555555-5555-5555-5555-555555555555',
     'DRAFT',
     '22222222-2222-2222-2222-222222222222',
     '33333333-3333-3333-3333-333333333333',
     '44444444-4444-4444-4444-444444444444',
     'PART_TIME',
     500.00, 50.00, 25.00, 100.00, 675.00, 'EUR',
     540.00, 54.00, 27.00, 108.00, 729.00, 'EUR',
     1);