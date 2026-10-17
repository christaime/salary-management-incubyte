-- Seed: reference data (countries, departments, roles, currency_rates).
-- Hand-written. Deterministic UUID literals only. ISO dates, NUMERIC money, TEXT enums.
--
-- Company profile: global media and entertainment group (streaming, film, music,
-- live events). Departments and roles reflect that, not a software company.
--
-- currency_rates: every row uses reference_currency_code = 'USD' and
-- reference_amount = 1, so a row reads as "1 USD = rate currency". Single
-- reference and no self-pair invariants are enforced by triggers, and this
-- file respects both. Conversion in later seed files:
--   amount_in_currency = ROUND(amount_in_USD * rate / reference_amount, 2)
--   amount_in_USD      = ROUND(amount_in_currency * reference_amount / rate, 2)

-- ---------------------------------------------------------------------------
-- Countries (10). Currency coverage: USD, EUR, INR, XAF, CAD, GBP, BRL, XOF, CNY.
-- Weighted region distribution is applied in the employee seeder, not here.
-- ---------------------------------------------------------------------------
INSERT INTO countries (id, iso_code, name, currency_code) VALUES
('c0000000-0000-0000-0000-000000000001', 'US', 'United States',  'USD'),
('c0000000-0000-0000-0000-000000000002', 'DE', 'Germany',        'EUR'),
('c0000000-0000-0000-0000-000000000003', 'FR', 'France',         'EUR'),
('c0000000-0000-0000-0000-000000000004', 'IN', 'India',          'INR'),
('c0000000-0000-0000-0000-000000000005', 'CM', 'Cameroon',       'XAF'),
('c0000000-0000-0000-0000-000000000006', 'CA', 'Canada',         'CAD'),
('c0000000-0000-0000-0000-000000000007', 'GB', 'United Kingdom', 'GBP'),
('c0000000-0000-0000-0000-000000000008', 'BR', 'Brazil',         'BRL'),
('c0000000-0000-0000-0000-000000000009', 'SN', 'Senegal',        'XOF'),
('c0000000-0000-0000-0000-00000000000a', 'CN', 'China',          'CNY');

-- ---------------------------------------------------------------------------
-- Departments (10). Media and entertainment group.
-- ---------------------------------------------------------------------------
INSERT INTO departments (id, code, name) VALUES
('d0000000-0000-0000-0000-000000000001', 'CONTENT',   'Content Production'),
('d0000000-0000-0000-0000-000000000002', 'STREAM',    'Streaming Platform'),
('d0000000-0000-0000-0000-000000000003', 'MUSIC',     'Music'),
('d0000000-0000-0000-0000-000000000004', 'LIVE',      'Live Events'),
('d0000000-0000-0000-0000-000000000005', 'MARKETING', 'Marketing'),
('d0000000-0000-0000-0000-000000000006', 'DISTRIB',   'Distribution'),
('d0000000-0000-0000-0000-000000000007', 'ADVERTIS',  'Advertising Sales'),
('d0000000-0000-0000-0000-000000000008', 'FINANCE',   'Finance'),
('d0000000-0000-0000-0000-000000000009', 'PEOPLE',    'People & Culture'),
('d0000000-0000-0000-0000-00000000000a', 'LEGAL',     'Legal & Rights');

-- ---------------------------------------------------------------------------
-- Roles (12). No seniority column on the table; seniority is folded into the
-- role name and reflected in the salary bands used by the employee seeder.
-- ---------------------------------------------------------------------------
INSERT INTO roles (id, code, name) VALUES
('r0000000-0000-0000-0000-000000000001', 'PROD',    'Producer'),
('r0000000-0000-0000-0000-000000000002', 'SPROD',   'Senior Producer'),
('r0000000-0000-0000-0000-000000000003', 'EDIT',    'Video Editor'),
('r0000000-0000-0000-0000-000000000004', 'ENG',     'Streaming Engineer'),
('r0000000-0000-0000-0000-000000000005', 'SENG',    'Senior Streaming Engineer'),
('r0000000-0000-0000-0000-000000000006', 'A&R',     'A&R Manager'),
('r0000000-0000-0000-0000-000000000007', 'EVENT',   'Event Coordinator'),
('r0000000-0000-0000-0000-000000000008', 'MKTG',    'Marketing Manager'),
('r0000000-0000-0000-0000-000000000009', 'SALES',   'Advertising Sales Executive'),
('r0000000-0000-0000-0000-00000000000a', 'DISTRIB', 'Distribution Analyst'),
('r0000000-0000-0000-0000-00000000000b', 'FIN',     'Financial Analyst'),
('r0000000-0000-0000-0000-00000000000c', 'LEGAL',   'Rights & Clearances Counsel');

-- ---------------------------------------------------------------------------
-- Currency rates. Single reference: USD. reference_amount = 1 on every row,
-- so each row reads "1 USD = rate currency". No self-pairs.
-- ---------------------------------------------------------------------------
INSERT INTO currency_rates
    (id, currency_code, reference_currency_code, reference_amount, rate) VALUES
('e0000000-0000-0000-0000-000000000001', 'EUR', 'USD', 1, 0.93),
('e0000000-0000-0000-0000-000000000002', 'INR', 'USD', 1, 83.00),
('e0000000-0000-0000-0000-000000000003', 'XAF', 'USD', 1, 583.51),
('e0000000-0000-0000-0000-000000000004', 'CAD', 'USD', 1, 1.37),
('e0000000-0000-0000-0000-000000000005', 'GBP', 'USD', 1, 0.79),
('e0000000-0000-0000-0000-000000000006', 'BRL', 'USD', 1, 5.56),
('e0000000-0000-0000-0000-000000000007', 'XOF', 'USD', 1, 583.51),
('e0000000-0000-0000-0000-000000000008', 'CNY', 'USD', 1, 7.14);