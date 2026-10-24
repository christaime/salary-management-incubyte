-- Seed for PayrollPeriodRepositoryAdapterTest.
-- Fixed UUIDs so the test can reference them by literal.

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
    ('11111111-1111-1111-1111-111111111111',
     'January 2026', '2026-01-01', '2026-01-31', 'MONTHLY', 0),
    ('22222222-2222-2222-2222-222222222222',
     'February 2026', '2026-02-01', '2026-02-28', 'MONTHLY', 0),
    ('33333333-3333-3333-3333-333333333333',
     'First Half of March 2026', '2026-03-01', '2026-03-15', 'BI_WEEKLY', 1);