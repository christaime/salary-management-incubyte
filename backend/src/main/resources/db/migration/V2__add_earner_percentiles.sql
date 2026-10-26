-- V2__add_earner_percentiles.sql
--
-- Introduces the earner percentile table (7 rows per group, annualized amounts
-- in the reference currency) and extends the earner ranking table with the
-- fields needed to rank by monthly rate instead of raw amount.
--
-- Amount columns use NUMERIC(38, 2), matching the other monetary tables in
-- this schema. (SQLite ignores the precision/scale parameters, but they
-- document intent and survive a future migration to a stricter DB.)

CREATE TABLE payroll_period_earner_percentiles (
    id                  TEXT PRIMARY KEY,
    payroll_period_id   TEXT NOT NULL,
    country_id          TEXT,
    department_id       TEXT,
    role_id             TEXT,
    employment_type     TEXT,
    percentile          INTEGER NOT NULL,
    reference_amount    NUMERIC(38, 2) NOT NULL,
    reference_currency  TEXT NOT NULL,
    employee_count      INTEGER NOT NULL,
    status              TEXT NOT NULL,
    CONSTRAINT uk_earner_percentile_group UNIQUE
        (payroll_period_id, country_id, department_id, role_id, employment_type, percentile),
    CONSTRAINT ck_earner_percentile_range
        CHECK (percentile BETWEEN 0 AND 100),
    CONSTRAINT ck_earner_percentile_population
        CHECK (employee_count > 0),
    CONSTRAINT fk_percentile_payroll_period
        FOREIGN KEY (payroll_period_id) REFERENCES payroll_periods (id),
    CONSTRAINT fk_percentile_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_percentile_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_percentile_role
        FOREIGN KEY (role_id) REFERENCES roles (id)
);

-- SQLite treats NULL as distinct in UNIQUE constraints, so the composite
-- constraint above does not protect the "global" group (all four dimension
-- columns NULL). This partial unique index does.
CREATE UNIQUE INDEX uk_earner_percentile_global
    ON payroll_period_earner_percentiles (payroll_period_id, percentile)
    WHERE country_id IS NULL
      AND department_id IS NULL
      AND role_id IS NULL
      AND employment_type IS NULL;

-- The composite UNIQUE constraint already provides a leftmost-prefix index on
-- (payroll_period_id, country_id, department_id, role_id, employment_type),
-- so no separate lookup index is needed.

-- Extend the ranking table with monthly-rate fields. Columns are nullable in
-- the DB because existing ranking rows (pre-migration) do not carry them; the
-- domain validator (@NotNull) and SnapshotBuilder are responsible for always
-- setting them on write.
ALTER TABLE payroll_period_earner_rankings ADD COLUMN days_covered          INTEGER;
ALTER TABLE payroll_period_earner_rankings ADD COLUMN monthly_rate_amount   NUMERIC(38, 2);
ALTER TABLE payroll_period_earner_rankings ADD COLUMN monthly_rate_currency TEXT;

-- Same global-group partial unique index for the ranking table.
-- (Scoped groups are already protected by the composite unique constraint
-- because at least one dimension column is non-NULL, and SQLite's NULL-in-
-- UNIQUE semantics then do the right thing.)
CREATE UNIQUE INDEX uk_earner_ranking_global
    ON payroll_period_earner_rankings (payroll_period_id, direction, "rank")
    WHERE country_id IS NULL
      AND department_id IS NULL
      AND role_id IS NULL
      AND employment_type IS NULL;