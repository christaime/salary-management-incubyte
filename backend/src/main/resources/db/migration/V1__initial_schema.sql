-- Milestone 2: initial schema for the salary management backend.
--
-- UUID columns are TEXT. Dates are TEXT. Booleans are BOOLEAN. Enum values are TEXT
-- and match the Java enum constant names, because the entities use @Enumerated(STRING).
--
-- Monetary columns are NUMERIC(38, 2). This matches Hibernate's default mapping for
-- BigDecimal, so schema validation passes without extra annotations.
--
-- Foreign keys are declared for documentation only. SQLite does not enforce them unless
-- PRAGMA foreign_keys = ON is set per connection; that is planned for a later milestone.

-- ---------------------------------------------------------------------------
-- countries
-- ---------------------------------------------------------------------------
CREATE TABLE countries (
    id             TEXT PRIMARY KEY,
    iso_code       TEXT NOT NULL,
    name           TEXT NOT NULL,
    currency_code  TEXT NOT NULL,
    CONSTRAINT uk_country_iso_code UNIQUE (iso_code)
);

CREATE INDEX idx_country_name ON countries (name);

-- ---------------------------------------------------------------------------
-- departments
-- ---------------------------------------------------------------------------
CREATE TABLE departments (
    id    TEXT PRIMARY KEY,
    code  TEXT NOT NULL,
    name  TEXT NOT NULL,
    CONSTRAINT uk_department_code UNIQUE (code),
    CONSTRAINT uk_department_name UNIQUE (name)
);

CREATE INDEX idx_department_code ON departments (code);

-- ---------------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id    TEXT PRIMARY KEY,
    code  TEXT NOT NULL,
    name  TEXT NOT NULL,
    CONSTRAINT uk_role_code UNIQUE (code),
    CONSTRAINT uk_role_name UNIQUE (name)
);

CREATE INDEX idx_role_code ON roles (code);

-- ---------------------------------------------------------------------------
-- employees
-- ---------------------------------------------------------------------------
CREATE TABLE employees (
    id                 TEXT PRIMARY KEY,
    matricule          TEXT NOT NULL,
    name               TEXT NOT NULL,
    email              TEXT NOT NULL,
    birth_date         TEXT,
    origin_country_id  TEXT,
    CONSTRAINT uk_employee_matricule UNIQUE (matricule),
    CONSTRAINT fk_employee_origin_country
        FOREIGN KEY (origin_country_id) REFERENCES countries (id)
);

CREATE INDEX idx_employee_matricule ON employees (matricule);
CREATE INDEX idx_employee_origin_country ON employees (origin_country_id);
CREATE INDEX idx_employee_name ON employees (name);

-- ---------------------------------------------------------------------------
-- currency_rates
-- A row is a sentence: reference_amount reference_currency_code = rate
-- currency_code. Example: (1, 'USD', 583.51, 'XAF') reads "1 USD = 583.51 XAF".
--
-- reference_amount is a real column, not an assumption: today every seeded row
-- has 1, but a row such as (100, 'JPY', 0.65, 'USD') = "100 JPY = 0.65 USD"
-- needs no schema change. Conversion always divides by reference_amount:
--   amount_in_currency_code = amount_in_reference * rate / reference_amount
--
-- Invariant: at any moment, every row in this table carries the same
-- reference_currency_code. It is the system's single reference currency.
-- The table is empty, or all rows agree. Changing the reference currency
-- requires DELETE all rows, then INSERT with the new reference.
-- Enforced by two triggers below, in addition to the adapter-level check.
-- ---------------------------------------------------------------------------
CREATE TABLE currency_rates (
    id                       TEXT PRIMARY KEY,
    currency_code            TEXT NOT NULL,
    reference_currency_code  TEXT NOT NULL,
    reference_amount         NUMERIC(38, 2) NOT NULL,
    rate                     NUMERIC(38, 2) NOT NULL,
    CONSTRAINT uk_currency_rate_pair UNIQUE (currency_code, reference_currency_code),
    CONSTRAINT ck_currency_rate_reference_amount_positive CHECK (reference_amount > 0),
    CONSTRAINT ck_currency_rate_rate_positive CHECK (rate > 0)
);

-- ---------------------------------------------------------------------------
-- Single-reference-currency triggers.
-- Reject any write that would leave the table with two different
-- reference_currency_code values. The table being empty is always allowed,
-- so the first INSERT after a full DELETE establishes the new reference.
-- ---------------------------------------------------------------------------
CREATE TRIGGER trg_currency_rate_single_reference_insert
BEFORE INSERT ON currency_rates
FOR EACH ROW
WHEN EXISTS (
    SELECT 1 FROM currency_rates
    WHERE reference_currency_code <> NEW.reference_currency_code
)
BEGIN
    SELECT RAISE(ABORT, 'currency_rates must use a single reference currency');
END;

CREATE TRIGGER trg_currency_rate_single_reference_update
BEFORE UPDATE OF reference_currency_code ON currency_rates
FOR EACH ROW
WHEN EXISTS (
    SELECT 1 FROM currency_rates
    WHERE reference_currency_code <> NEW.reference_currency_code
)
BEGIN
    SELECT RAISE(ABORT, 'currency_rates must use a single reference currency');
END;

-- ---------------------------------------------------------------------------
-- Self-pair triggers.
-- A row whose currency_code equals its reference_currency_code is a no-op
-- conversion (X to X). It is allowed, but must be stored as the identity:
-- reference_amount = 1 and rate = 1. Anything else on a self-pair is a bug.
-- ---------------------------------------------------------------------------
CREATE TRIGGER trg_currency_rate_self_pair_insert
BEFORE INSERT ON currency_rates
FOR EACH ROW
WHEN NEW.currency_code = NEW.reference_currency_code
 AND (NEW.reference_amount <> 1 OR NEW.rate <> 1)
BEGIN
    SELECT RAISE(ABORT,
        'currency_rates self-pair must have reference_amount = 1 and rate = 1');
END;

CREATE TRIGGER trg_currency_rate_self_pair_update
BEFORE UPDATE OF currency_code, reference_currency_code, reference_amount, rate
ON currency_rates
FOR EACH ROW
WHEN NEW.currency_code = NEW.reference_currency_code
 AND (NEW.reference_amount <> 1 OR NEW.rate <> 1)
BEGIN
    SELECT RAISE(ABORT,
        'currency_rates self-pair must have reference_amount = 1 and rate = 1');
END;
-- ---------------------------------------------------------------------------
-- payroll_periods
-- ---------------------------------------------------------------------------
CREATE TABLE payroll_periods (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    start_date  TEXT NOT NULL,
    end_date    TEXT NOT NULL,
    frequency   TEXT NOT NULL,
    closed      BOOLEAN NOT NULL,
    CONSTRAINT uk_payroll_period_name UNIQUE (name),
    CONSTRAINT ck_payroll_period_dates CHECK (start_date < end_date)
);

CREATE INDEX idx_payroll_period_name ON payroll_periods (name);
CREATE INDEX idx_payroll_period_dates ON payroll_periods (start_date, end_date);

-- ---------------------------------------------------------------------------
-- employee_periodic_assignments
-- ---------------------------------------------------------------------------
CREATE TABLE employee_periodic_assignments (
    id               TEXT PRIMARY KEY,
    employee_id      TEXT NOT NULL,
    start_date       TEXT NOT NULL,
    end_date         TEXT,
    country_id       TEXT NOT NULL,
    department_id    TEXT NOT NULL,
    role_id          TEXT NOT NULL,
    employment_type  TEXT NOT NULL,
    frequency        TEXT NOT NULL,
    in_effect        BOOLEAN NOT NULL,
    CONSTRAINT fk_assignment_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_assignment_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_assignment_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_assignment_role
        FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT ck_assignment_dates
        CHECK (end_date IS NULL OR start_date < end_date)
);

CREATE INDEX idx_assignment_employee ON employee_periodic_assignments (employee_id);
CREATE INDEX idx_assignment_country ON employee_periodic_assignments (country_id);
CREATE INDEX idx_assignment_department ON employee_periodic_assignments (department_id);
CREATE INDEX idx_assignment_role ON employee_periodic_assignments (role_id);
CREATE INDEX idx_assignment_frequency ON employee_periodic_assignments (frequency);
CREATE INDEX idx_assignment_dates
    ON employee_periodic_assignments (start_date, end_date);
-- ---------------------------------------------------------------------------
-- payroll_summaries
-- ---------------------------------------------------------------------------
CREATE TABLE payroll_summaries (
    id                                TEXT PRIMARY KEY,
    employee_id                       TEXT NOT NULL,
    payroll_period_id                 TEXT NOT NULL,
    employee_periodic_assignment_id   TEXT NOT NULL,
    assignment_country_id             TEXT NOT NULL,
    assignment_department_id          TEXT NOT NULL,
    assignment_role_id                TEXT NOT NULL,
    assignment_employment_type        TEXT NOT NULL,
    base                              NUMERIC(38, 2) NOT NULL,
    variable                          NUMERIC(38, 2) NOT NULL,
    allowances                        NUMERIC(38, 2) NOT NULL,
    employer_taxes                    NUMERIC(38, 2) NOT NULL,
    total                             NUMERIC(38, 2) NOT NULL,
    currency                          TEXT NOT NULL,
    status                            TEXT NOT NULL,
    CONSTRAINT uk_payroll_summary_employee_period
        UNIQUE (employee_id, payroll_period_id),
    CONSTRAINT fk_summary_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_summary_payroll_period
        FOREIGN KEY (payroll_period_id) REFERENCES payroll_periods (id),
    CONSTRAINT fk_summary_assignment
        FOREIGN KEY (employee_periodic_assignment_id)
            REFERENCES employee_periodic_assignments (id),
    CONSTRAINT fk_summary_assignment_country
        FOREIGN KEY (assignment_country_id) REFERENCES countries (id),
    CONSTRAINT fk_summary_assignment_department
        FOREIGN KEY (assignment_department_id) REFERENCES departments (id),
    CONSTRAINT fk_summary_assignment_role
        FOREIGN KEY (assignment_role_id) REFERENCES roles (id),
    CONSTRAINT ck_payroll_summary_total
        CHECK (total = base + variable + allowances + employer_taxes)
);

CREATE INDEX idx_payroll_summary_employee ON payroll_summaries (employee_id);
CREATE INDEX idx_payroll_summary_payroll_period ON payroll_summaries (payroll_period_id);
CREATE INDEX idx_payroll_summary_assignment ON payroll_summaries (employee_periodic_assignment_id);
CREATE INDEX idx_payroll_summary_status ON payroll_summaries (status);
CREATE INDEX idx_payroll_summary_assignment_country
    ON payroll_summaries (assignment_country_id);
CREATE INDEX idx_payroll_summary_assignment_department
    ON payroll_summaries (assignment_department_id);
CREATE INDEX idx_payroll_summary_assignment_role
    ON payroll_summaries (assignment_role_id);
CREATE INDEX idx_payroll_summary_assignment_employment_type
    ON payroll_summaries (assignment_employment_type);
CREATE INDEX idx_payroll_summary_period_start
    ON payroll_summaries (payroll_period_id, status);


-- ---------------------------------------------------------------------------
-- payroll_period_aggregates
-- ---------------------------------------------------------------------------
CREATE TABLE payroll_period_aggregates (
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
        CHECK (original_total =
               original_base + original_variable + original_allowances + original_employer_taxes),
    CONSTRAINT ck_aggregate_reference_total
        CHECK (reference_total =
               reference_base + reference_variable + reference_allowances + reference_employer_taxes)
);

CREATE INDEX idx_payroll_period_aggregate_payroll_period
    ON payroll_period_aggregates (payroll_period_id);
CREATE INDEX idx_payroll_period_aggregate_group
    ON payroll_period_aggregates (country_id, department_id, role_id, employment_type);


-- ---------------------------------------------------------------------------
-- payroll_period_earner_rankings
--
-- Each row carries the employee's total in two currencies: the assignment
-- currency (original_amount / original_currency) and the reference currency
-- (reference_amount / reference_currency). This mirrors
-- payroll_period_aggregates, so no downstream code has to convert or guess.
-- ---------------------------------------------------------------------------
CREATE TABLE payroll_period_earner_rankings (
    id                  TEXT PRIMARY KEY,
    payroll_period_id   TEXT NOT NULL,
    status              TEXT NOT NULL,
    country_id          TEXT NOT NULL,
    department_id       TEXT NOT NULL,
    role_id             TEXT NOT NULL,
    employment_type     TEXT NOT NULL,
    direction           TEXT NOT NULL,
    rank                INTEGER NOT NULL,
    employee_id         TEXT NOT NULL,
    original_amount     NUMERIC(38, 2) NOT NULL,
    original_currency   TEXT NOT NULL,
    reference_amount    NUMERIC(38, 2) NOT NULL,
    reference_currency  TEXT NOT NULL,
    CONSTRAINT uk_payroll_period_earner_ranking_group
        UNIQUE (payroll_period_id, country_id, department_id, role_id,
                employment_type, direction, rank),
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

CREATE INDEX idx_payroll_period_earner_ranking_payroll_period
    ON payroll_period_earner_rankings (payroll_period_id);
CREATE INDEX idx_payroll_period_earner_ranking_employee
    ON payroll_period_earner_rankings (employee_id);