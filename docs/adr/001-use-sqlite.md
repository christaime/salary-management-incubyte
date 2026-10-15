# 001. Use SQLite as the database

## Status

Accepted

## Context

The system stores payroll data for 10,000 employees across multiple countries.
The obvious choice for this domain is PostgreSQL — it is the default for
relational data in production systems, it enforces constraints, and it supports
analytical functions.

However, this is a take-home assessment. A reviewer will clone the repository
and expect to run it in under five minutes. Requiring a PostgreSQL installation
would add friction that has nothing to do with the quality of the solution.

The workload itself is read-heavy and single-user. Writes happen during data
entry and period close; the rest of the time the system is answering queries.
There is no concurrency requirement.

## Decision

Use SQLite as the database for this assessment.

The application is written so that switching to PostgreSQL requires only a
datasource configuration change — no application code changes.

## Consequences

**Easier:**

- A reviewer can clone the repository and run it without installing or
  configuring a database server.
- The repository is self-contained. There is no external service dependency.
- Tests run without containers or a shared database.
- Backup is a file copy, and the database can be inspected with any SQLite
  client.

**Harder:**

- SQLite does not enforce `NOT NULL` and `FOREIGN KEY` by default. These are
  enabled via `PRAGMA`, but some constraints remain application-level.
- SQLite does not support exclusion constraints. Overlap prevention for payroll
  periods and employee assignments is enforced in the service layer.
- Concurrent writes are not supported. This is acceptable for a single-user
  system; it would not be for a multi-user deployment.

**In production:**

A multi-user HR team would use PostgreSQL — for MVCC, enforced constraints, and
richer analytical functions. The datasource URL, driver, and dialect change; the
application code does not.