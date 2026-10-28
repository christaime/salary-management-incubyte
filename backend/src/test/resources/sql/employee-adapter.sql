-- Seed for EmployeeRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR'),
    ('22222222-2222-2222-2222-222222222222', 'DE', 'Germany', 'EUR');

-- Reference data for the assignment filters.
INSERT INTO departments (id, code, name) VALUES
    ('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering'),
    ('44444444-4444-4444-4444-444444444444', 'FIN', 'Finance');

INSERT INTO roles (id, code, name) VALUES
    ('55555555-5555-5555-5555-555555555555', 'SWE', 'Engineer'),
    ('66666666-6666-6666-6666-666666666666', 'MGR', 'Manager');

INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     'EMP-0001', 'Ada Lovelace', 'ada@example.com',
     '1815-12-10', '11111111-1111-1111-1111-111111111111'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     'EMP-0002', 'Grace Hopper', 'grace@example.com',
     '1906-12-09', '22222222-2222-2222-2222-222222222222'),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc',
     'EMP-0003', 'Alan Turing', 'alan@example.com',
     NULL, NULL);

-- Assignments used by the assignment filter tests.
--   ADA   -> FR / ENG / SWE / FULL_TIME  (in effect)
--   GRACE -> DE / FIN / MGR / PART_TIME  (in effect)
--   ALAN  -> no assignment
INSERT INTO employee_periodic_assignments
    (id, employee_id, start_date, end_date, country_id, department_id, role_id,
     employment_type, frequency, in_effect)
VALUES
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '2025-10-01', NULL,
     '11111111-1111-1111-1111-111111111111',
     '33333333-3333-3333-3333-333333333333',
     '55555555-5555-5555-5555-555555555555',
     'FULL_TIME', 'MONTHLY', 1),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2',
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     '2025-10-01', NULL,
     '22222222-2222-2222-2222-222222222222',
     '44444444-4444-4444-4444-444444444444',
     '66666666-6666-6666-6666-666666666666',
     'PART_TIME', 'BI_WEEKLY', 1);