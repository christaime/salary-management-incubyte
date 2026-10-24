-- Seed for RoleRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.
-- Codes are stable; the tests assert on them for existsByCode / code filters / keyword.

INSERT INTO roles (id, code, name) VALUES
    ('11111111-1111-1111-1111-111111111111', 'SWE', 'Engineer'),
    ('22222222-2222-2222-2222-222222222222', 'MGR', 'Manager'),
    ('33333333-3333-3333-3333-333333333333', 'ANL', 'Analyst');