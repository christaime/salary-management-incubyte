-- statistics-unconvertible.sql
--
-- ADDITIVE fixture applied on top of statistics-base.sql for the two
-- special-case statistics tests:
--
--   P8  2026-08-01 … 2026-08-31  MONTHLY
--       E26's only VALIDATED summary is in XYZ. XYZ has no currency_rates
--       row in the base fixture, so the summary is unconvertible and the
--       WHERE clause drops it before toProjection runs. minMax returns an
--       empty list. (Test E1.)
--
--   P9  2026-09-01 … 2026-09-30  MONTHLY
--       E27's assignment_country_id points at a UUID that is NOT present
--       in `countries`. The FK on payroll_summaries is documentation-only
--       in SQLite (see V1 header), so the row is insertable; toProjection
--       then calls countryJpaRepository.findById → miss →
--       IllegalStateException. (Test E13.)
--
-- The script assumes statistics-base.sql has already been applied in the
-- same test method. It only INSERTs — no DELETE, no UPDATE, no id collisions
-- with the base fixture:
--   periods     …0608, …0609          (base ends at …0607)
--   employees   …041a, …041b          (base ends at …0419)
--   assignments …051a, …051b          (base ends at …0519)
--   summaries   …071d, …071e          (base ends at …071c)

-- ---------------------------------------------------------------------------
-- Extra periods
-- ---------------------------------------------------------------------------
INSERT INTO payroll_periods (id, name, start_date, end_date, frequency, closed) VALUES
  ('00000000-0000-4000-a000-000000000608', 'P8-2026-08', '2026-08-01', '2026-08-31', 'MONTHLY', FALSE),
  ('00000000-0000-4000-a000-000000000609', 'P9-2026-09', '2026-09-01', '2026-09-30', 'MONTHLY', FALSE);

-- ---------------------------------------------------------------------------
-- Extra employees
-- ---------------------------------------------------------------------------
INSERT INTO employees (id, matricule, name, email, birth_date, origin_country_id) VALUES
  ('00000000-0000-4000-a000-00000000041a', 'M-001A', 'Zulu 26',  'ba@example.com', '1990-01-01', '00000000-0000-4000-a000-000000000101'),
  ('00000000-0000-4000-a000-00000000041b', 'M-001B', 'Alpha 27', 'bb@example.com', '1990-01-01', '00000000-0000-4000-a000-000000000101');

-- ---------------------------------------------------------------------------
-- Extra assignments
-- E27's assignment carries a real country_id (USA) — the dangling reference
-- for E13 is on the SUMMARY row, not on the assignment, because
-- toProjection reads `assignment_country_id` from payroll_summaries.
-- ---------------------------------------------------------------------------
INSERT INTO employee_periodic_assignments
  (id, employee_id, start_date, end_date, country_id, department_id, role_id,
   employment_type, frequency, in_effect) VALUES
  ('00000000-0000-4000-a000-00000000051a', '00000000-0000-4000-a000-00000000041a', '2026-08-01', NULL,
   '00000000-0000-4000-a000-000000000101', '00000000-0000-4000-a000-000000000201',
   '00000000-0000-4000-a000-000000000301', 'FULL_TIME', 'MONTHLY', TRUE),
  ('00000000-0000-4000-a000-00000000051b', '00000000-0000-4000-a000-00000000041b', '2026-09-01', NULL,
   '00000000-0000-4000-a000-000000000101', '00000000-0000-4000-a000-000000000201',
   '00000000-0000-4000-a000-000000000301', 'FULL_TIME', 'MONTHLY', TRUE);

-- ---------------------------------------------------------------------------
-- Extra summaries
--   …071d — E26, P8, currency XYZ, VALIDATED  → unconvertible (E1)
--   …071e — E27, P9, USD, but assignment_country_id = …00dead (E13)
-- ---------------------------------------------------------------------------
INSERT INTO payroll_summaries
  (id, employee_id, payroll_period_id, employee_periodic_assignment_id,
   assignment_country_id, assignment_department_id, assignment_role_id,
   assignment_employment_type, base, variable, allowances, employer_taxes,
   total, currency, status) VALUES
  ('00000000-0000-4000-a000-00000000071d', '00000000-0000-4000-a000-00000000041a', '00000000-0000-4000-a000-000000000608', '00000000-0000-4000-a000-00000000051a',
   '00000000-0000-4000-a000-000000000101', '00000000-0000-4000-a000-000000000201', '00000000-0000-4000-a000-000000000301',
   'FULL_TIME', 2700.00, 300.00, 0.00, 0.00, 3000.00, 'XYZ', 'VALIDATED'),
  ('00000000-0000-4000-a000-00000000071e', '00000000-0000-4000-a000-00000000041b', '00000000-0000-4000-a000-000000000609', '00000000-0000-4000-a000-00000000051b',
   '00000000-0000-4000-a000-00000000dead', '00000000-0000-4000-a000-000000000201', '00000000-0000-4000-a000-000000000301',
   'FULL_TIME',  900.00, 100.00, 0.00, 0.00, 1000.00, 'USD', 'VALIDATED');