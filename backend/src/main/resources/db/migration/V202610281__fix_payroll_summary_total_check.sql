
-------------
-- Amount columns use NUMERIC(38, 2), matching the other monetary tables in
-- this schema. (SQLite ignores the precision/scale parameters, but they
-- document intent and survive a future migration to a stricter DB.)
---------------------------

-- 1. Defensive: normalise any rows where total ≠ sum (no-op if clean)
UPDATE payroll_summaries
SET total = ROUND(base + variable + allowances + employer_taxes, 2)
WHERE ROUND(total, 2) <>
      ROUND(base + variable + allowances + employer_taxes, 2);

-- 2. Create the new table with the ROUND-based CHECK
CREATE TABLE payroll_summaries_new (
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
        CHECK (ROUND(total, 2) =
               ROUND(base + variable + allowances + employer_taxes, 2))
);

-- 3. Copy data
INSERT INTO payroll_summaries_new
SELECT id, employee_id, payroll_period_id, employee_periodic_assignment_id,
       assignment_country_id, assignment_department_id, assignment_role_id,
       assignment_employment_type, base, variable, allowances,
       employer_taxes, total, currency, status
FROM payroll_summaries;

-- 4. Drop old, rename new
DROP TABLE payroll_summaries;
ALTER TABLE payroll_summaries_new RENAME TO payroll_summaries;

-- 5. Recreate indexes (check your V1 for the exact list)
CREATE INDEX idx_payroll_summary_employee              ON payroll_summaries (employee_id);
CREATE INDEX idx_payroll_summary_payroll_period        ON payroll_summaries (payroll_period_id);
CREATE INDEX idx_payroll_summary_assignment            ON payroll_summaries (employee_periodic_assignment_id);
CREATE INDEX idx_payroll_summary_status                ON payroll_summaries (status);
CREATE INDEX idx_payroll_summary_assignment_country    ON payroll_summaries (assignment_country_id);
CREATE INDEX idx_payroll_summary_assignment_department ON payroll_summaries (assignment_department_id);
CREATE INDEX idx_payroll_summary_assignment_role       ON payroll_summaries (assignment_role_id);
CREATE INDEX idx_payroll_summary_assignment_employment_type
    ON payroll_summaries (assignment_employment_type);
CREATE INDEX idx_payroll_summary_period_start
    ON payroll_summaries (payroll_period_id, status);