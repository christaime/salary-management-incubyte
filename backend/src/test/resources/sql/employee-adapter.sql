-- Seed for EmployeeRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
    ('11111111-1111-1111-1111-111111111111', 'FR', 'France', 'EUR'),
    ('22222222-2222-2222-2222-222222222222', 'DE', 'Germany', 'EUR');

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