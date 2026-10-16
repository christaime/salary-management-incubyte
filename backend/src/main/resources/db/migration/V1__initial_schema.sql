-- Milestone 2: initial schema for the salary management backend.
--
-- UUID columns are TEXT. Dates are TEXT in ISO-8601 (YYYY-MM-DD). Enum values are TEXT
-- and match the Java enum constant names, because the entities use @Enumerated(STRING).
--
-- Monetary columns are TEXT, not REAL. SQLite REAL is IEEE-754 binary floating point and
-- would silently lose precision on money; TEXT stores the exact BigDecimal decimal string
-- and round-trips it verbatim.
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
    name  TEXT NOT NULL,
    CONSTRAINT uk_department_name UNIQUE (name)
);

-- ---------------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id    TEXT PRIMARY KEY,
    name  TEXT NOT NULL,
    CONSTRAINT uk_role_name UNIQUE (name)
);

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
-- ---------------------------------------------------------------------------
CREATE TABLE currency_rates (
    id                       TEXT PRIMARY KEY,
    currency_code            TEXT NOT NULL,
    reference_currency_code  TEXT NOT NULL,
    rate                     TEXT NOT NULL,
    CONSTRAINT uk_currency_rate_pair UNIQUE (currency_code, reference_currency_code)
);

-- ---------------------------------------------------------------------------
-- payroll_periods
-- ---------------------------------------------------------------------------
CREATE TABLE payroll_periods (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    start_date  TEXT NOT NULL,
    end_date    TEXT NOT NULL,
    frequency   TEXT NOT NULL,
    closed      INTEGER NOT NULL,
    CONSTRAINT uk_payroll_period_name UNIQUE (name)
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
    in_effect        INTEGER NOT NULL,
    CONSTRAINT fk_assignment_employee
        FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_assignment_country
        FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_assignment_department
        FOREIGN KEY (department_id) REFERENCES departments (id),
    CONSTRAINT fk_assignment_role
        FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_assignment_employee ON employee_periodic_assignments (employee_id);
CREATE INDEX idx_assignment_country ON employee_periodic_assignments (country_id);
CREATE INDEX idx_assignment_department ON employee_periodic_assignments (department_id);
CREATE INDEX idx_assignment_role ON employee_periodic_assignments (role_id);
CREATE INDEX idx_assignment_dates
    ON employee_periodic_assignments (start_date, end_date);

-- ---------------------------------------------------------------------------
-- payroll_summaries
--
-- employee_id and payroll_period_id are the target of @ManyToOne associations on the entity;
-- the columns keep their original names. employee_periodic_assignment_id is a plain UUID, and
-- the assignment's country, department, role, and employment type are denormalized into the
-- summary so filtering by assignment attributes does not require a join.
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
    base                              TEXT NOT NULL,
    variable                          TEXT NOT NULL,
    allowances                        TEXT NOT NULL,
    employer_taxes                    TEXT NOT NULL,
    total                             TEXT NOT NULL,
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
        FOREIGN KEY (assignment_role_id) REFERENCES roles (id)
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
    original_base            TEXT NOT NULL,
    original_variable        TEXT NOT NULL,
    original_allowances      TEXT NOT NULL,
    original_employer_taxes  TEXT NOT NULL,
    original_total           TEXT NOT NULL,
    original_currency        TEXT NOT NULL,
    reference_base           TEXT NOT NULL,
    reference_variable       TEXT NOT NULL,
    reference_allowances     TEXT NOT NULL,
    reference_employer_taxes TEXT NOT NULL,
    reference_total          TEXT NOT NULL,
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
        FOREIGN KEY (role_id) REFERENCES roles (id)
);

CREATE INDEX idx_payroll_period_aggregate_payroll_period
    ON payroll_period_aggregates (payroll_period_id);
CREATE INDEX idx_payroll_period_aggregate_group
    ON payroll_period_aggregates (country_id, department_id, role_id, employment_type);

-- ---------------------------------------------------------------------------
-- payroll_period_earner_rankings
-- ---------------------------------------------------------------------------
CREATE TABLE payroll_period_earner_rankings (
    id                TEXT PRIMARY KEY,
    payroll_period_id TEXT NOT NULL,
    status            TEXT NOT NULL,
    country_id        TEXT NOT NULL,
    department_id     TEXT NOT NULL,
    role_id           TEXT NOT NULL,
    employment_type   TEXT NOT NULL,
    direction         TEXT NOT NULL,
    rank              INTEGER NOT NULL,
    employee_id       TEXT NOT NULL,
    amount            TEXT NOT NULL,
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