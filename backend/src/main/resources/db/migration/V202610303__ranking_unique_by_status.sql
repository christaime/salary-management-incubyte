-- V202610304__ranking_unique_by_status.sql
--
-- The unique key on payroll_period_earner_rankings does not include `status`.
-- A DRAFT snapshot therefore cannot coexist with a VALIDATED snapshot for the
-- same (period, direction, employee). deleteDraftsByPayrollPeriodId removes
-- only DRAFT rows (by design: VALIDATED snapshots are immutable), so the
-- remaining VALIDATED rows block the new DRAFT INSERTs on (period, direction,
-- employee). The failure shows up as:
--   UNIQUE constraint failed (payroll_period_id, direction, employee_id)
-- on a re-run, when the previous run left VALIDATED rankings behind.
--
-- Fix: include `status` in the unique key and in each partial index, so a
-- DRAFT snapshot and a VALIDATED snapshot for the same rank position coexist.
-- SQLite cannot ALTER ... DROP CONSTRAINT, so the table is rebuilt.

CREATE TABLE payroll_period_earner_rankings_new (
    id                     TEXT PRIMARY KEY,
    payroll_period_id      TEXT NOT NULL,
    status                 TEXT NOT NULL,
    country_id             TEXT,
    department_id          TEXT,
    role_id                TEXT,
    employment_type        TEXT,
    direction              TEXT NOT NULL,
    rank                   INTEGER NOT NULL,
    employee_id            TEXT NOT NULL,
    original_amount        NUMERIC(38, 2) NOT NULL,
    original_currency      TEXT NOT NULL,
    reference_amount       NUMERIC(38, 2) NOT NULL,
    reference_currency     TEXT NOT NULL,
    days_covered           INTEGER,
    monthly_rate_amount    NUMERIC(38, 2),
    monthly_rate_currency  TEXT,
    CONSTRAINT uk_payroll_period_earner_ranking_group
        UNIQUE (payroll_period_id, country_id, department_id, role_id,
                employment_type, direction, employee_id, status),
    CONSTRAINT fk_ranking_payroll_period
        FOREIGN KEY (payroll_period_id) REFERENCES payroll_periods (id),
    CONSTRAINT fk_ranking_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_ranking_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_ranking_role
        FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_ranking_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id)
);

INSERT INTO payroll_period_earner_rankings_new
    (id, payroll_period_id, status,
     country_id, department_id, role_id, employment_type,
     direction, rank, employee_id,
     original_amount, original_currency, reference_amount, reference_currency,
     days_covered, monthly_rate_amount, monthly_rate_currency)
SELECT id, payroll_period_id, status,
       country_id, department_id, role_id, employment_type,
       direction, rank, employee_id,
       original_amount, original_currency, reference_amount, reference_currency,
       days_covered, monthly_rate_amount, monthly_rate_currency
FROM payroll_period_earner_rankings;

DROP TABLE payroll_period_earner_rankings;
ALTER TABLE payroll_period_earner_rankings_new
    RENAME TO payroll_period_earner_rankings;

CREATE INDEX idx_payroll_period_earner_ranking_payroll_period
    ON payroll_period_earner_rankings (payroll_period_id);
CREATE INDEX idx_payroll_period_earner_ranking_employee
    ON payroll_period_earner_rankings (employee_id);

-- Partial unique indexes: include `status`.
CREATE UNIQUE INDEX uk_ranking_by_country
    ON payroll_period_earner_rankings
       (payroll_period_id, country_id, direction, employee_id, status)
    WHERE country_id IS NOT NULL
      AND department_id IS NULL AND role_id IS NULL AND employment_type IS NULL;

CREATE UNIQUE INDEX uk_ranking_by_department
    ON payroll_period_earner_rankings
       (payroll_period_id, department_id, direction, employee_id, status)
    WHERE department_id IS NOT NULL
      AND country_id IS NULL AND role_id IS NULL AND employment_type IS NULL;

CREATE UNIQUE INDEX uk_ranking_by_role
    ON payroll_period_earner_rankings
       (payroll_period_id, role_id, direction, employee_id, status)
    WHERE role_id IS NOT NULL
      AND country_id IS NULL AND department_id IS NULL AND employment_type IS NULL;

CREATE UNIQUE INDEX uk_ranking_by_employment_type
    ON payroll_period_earner_rankings
       (payroll_period_id, employment_type, direction, employee_id, status)
    WHERE employment_type IS NOT NULL
      AND country_id IS NULL AND department_id IS NULL AND role_id IS NULL;

CREATE UNIQUE INDEX uk_ranking_global
    ON payroll_period_earner_rankings
       (payroll_period_id, direction, employee_id, status)
    WHERE country_id IS NULL AND department_id IS NULL AND role_id IS NULL
      AND employment_type IS NULL;