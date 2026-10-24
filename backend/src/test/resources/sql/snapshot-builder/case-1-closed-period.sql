-- Case 1: snapshot is asked for a closed period. The builder must return a
-- PERIOD_CLOSED blocker and stop before doing any other work.

INSERT INTO countries (id, iso_code, name, currency_code) VALUES
('11111111-1111-1111-1111-111111111111', 'US', 'United States', 'USD');

INSERT INTO departments (id, code, name) VALUES
('33333333-3333-3333-3333-333333333333', 'ENG', 'Engineering');

INSERT INTO roles (id, code, name) VALUES
('44444444-4444-4444-4444-444444444444', 'SWE', 'Software Engineer');

INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
('66666666-6666-6666-6666-666666666666', '2026-02 Monthly',
 '2026-02-01', '2026-02-28', 'MONTHLY', 1);