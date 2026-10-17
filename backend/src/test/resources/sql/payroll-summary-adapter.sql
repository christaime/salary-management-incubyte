-- Seed for PayrollSummaryRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR');

INSERT INTO departments (id, code, name) VALUES
    ('22222222-2222-2222-2222-222222222222', 'ENG', 'Engineering');

INSERT INTO roles (id, code, name) VALUES
    ('33333333-3333-3333-3333-333333333333', 'SWE', 'Engineer');

INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'EMP-0001', 'Ada Lovelace', 'ada@example.com',
     '1815-12-10', '11111111-1111-1111-1111-111111111111'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'EMP-0002', 'Grace Hopper', 'grace@example.com',
     '1906-12-09', '11111111-1111-1111-1111-111111111111');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
    ('44444444-4444-4444-4444-444444444444',
     'January 2026', '2026-01-01', '2026-01-31', 'MONTHLY', 0),
    ('99999999-9999-9999-9999-999999999999',
     'February 2026', '2026-02-01', '2026-02-28', 'MONTHLY', 0);

INSERT INTO employee_periodic_assignments
    (id, employee_id, start_date, end_date, country_id,
     department_id, role_id, employment_type, in_effect)
VALUES
    ('55555555-5555-5555-5555-555555555555',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '2026-01-01', NULL,
     '11111111-1111-1111-1111-111111111111',
     '22222222-2222-2222-2222-222222222222',
     '33333333-3333-3333-3333-333333333333',
     'FULL_TIME', 1);

INSERT INTO payroll_summaries
    (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
     assignment_country_id, assignment_department_id, assignment_role_id,
     assignment_employment_type, base, variable, allowances, employer_taxes,
     total, currency, status)
VALUES
    ('66666666-6666-6666-6666-666666666666',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '44444444-4444-4444-4444-444444444444',
     '55555555-5555-5555-5555-555555555555',
     '11111111-1111-1111-1111-111111111111',
     '22222222-2222-2222-2222-222222222222',
     '33333333-3333-3333-3333-333333333333',
     'FULL_TIME', 1000.00, 100.00, 50.00, 200.00, 1350.00, 'EUR', 'DRAFT');