-------------
-- Amount columns use NUMERIC(38, 2), matching the other monetary tables in
-- this schema. SQLite stores NUMERIC as REAL/INTEGER, so a raw equality
-- CHECK (total = a + b + c + d) can fail on floating-point round-off even
-- when the values were written at scale 2. The fix mirrors
-- V202610281__fix_payroll_summary_total_check.sql:
--   (a) defensively normalise any rows where the sum is off at scale 2;
--   (b) rebuild the table (SQLite has no DROP CONSTRAINT) with a
--       ROUND(..., 2)-based CHECK on both totals;
--   (c) copy data, swap tables, recreate indexes and the unique constraint.
---------------------------

-- 1. Defensive: normalise any rows where either total ≠ sum (no-op if clean)
UPDATE payroll_period_aggregates
SET original_total = ROUND(original_base + original_variable
                           + original_allowances + original_employer_taxes, 2)
WHERE ROUND(original_total, 2) <>
      ROUND(original_base + original_variable
            + original_allowances + original_employer_taxes, 2);

UPDATE payroll_period_aggregates
SET reference_total = ROUND(reference_base + reference_variable
                            + reference_allowances + reference_employer_taxes, 2)
WHERE ROUND(reference_total, 2) <>
      ROUND(reference_base + reference_variable
            + reference_allowances + reference_employer_taxes, 2);

-- 2. Create the new table with ROUND-based CHECKs on both totals
CREATE TABLE payroll_period_aggregates_new (
    id                       TEXT PRIMARY KEY,
    payroll_period_id        TEXT NOT NULL,
    status                   TEXT NOT NULL,
    country_id               TEXT NOT NULL,
    department_id            TEXT NOT NULL,
    role_id                  TEXT NOT NULL,
    employment_type          TEXT NOT NULL,
    original_base            NUMERIC(38, 2) NOT NULL,
    original_variable        NUMERIC(38, 2) NOT NULL,
    original_allowances      NUMERIC(38, 2) NOT NULL,
    original_employer_taxes  NUMERIC(38, 2) NOT NULL,
    original_total           NUMERIC(38, 2) NOT NULL,
    original_currency        TEXT NOT NULL,
    reference_base           NUMERIC(38, 2) NOT NULL,
    reference_variable       NUMERIC(38, 2) NOT NULL,
    reference_allowances     NUMERIC(38, 2) NOT NULL,
    reference_employer_taxes NUMERIC(38, 2) NOT NULL,
    reference_total          NUMERIC(38, 2) NOT NULL,
    reference_currency       TEXT NOT NULL,
    employee_count           INTEGER NOT NULL,
    CONSTRAINT uk_payroll_period_aggregate_group
        UNIQUE (payroll_period_id, country_id, department_id, role_id, employment_type),
    CONSTRAINT fk_aggregate_payroll_period
        FOREIGN KEY (payroll_period_id) REFERENCES payroll_periods (id),
    CONSTRAINT fk_aggregate_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_aggregate_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_aggregate_role
        FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT ck_aggregate_original_total
        CHECK (ROUND(original_total, 2) =
               ROUND(original_base + original_variable
                     + original_allowances + original_employer_taxes, 2)),
    CONSTRAINT ck_aggregate_reference_total
        CHECK (ROUND(reference_total, 2) =
               ROUND(reference_base + reference_variable
                     + reference_allowances + reference_employer_taxes, 2))
);

-- 3. Copy data
INSERT INTO payroll_period_aggregates_new
SELECT id, payroll_period_id, status,
       country_id, department_id, role_id, employment_type,
       original_base, original_variable, original_allowances,
       original_employer_taxes, original_total, original_currency,
       reference_base, reference_variable, reference_allowances,
       reference_employer_taxes, reference_total, reference_currency,
       employee_count
FROM payroll_period_aggregates;

-- 4. Drop old, rename new
DROP TABLE payroll_period_aggregates;
ALTER TABLE payroll_period_aggregates_new RENAME TO payroll_period_aggregates;

-- 5. Recreate indexes (same list as V1)
CREATE INDEX idx_payroll_period_aggregate_payroll_period
    ON payroll_period_aggregates (payroll_period_id);
CREATE INDEX idx_payroll_period_aggregate_group
    ON payroll_period_aggregates (country_id, department_id, role_id, employment_type);