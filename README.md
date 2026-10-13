# Salary Management — Incubyte Assessment

> A web-based salary management platform for ACME's HR team — replacing
> spreadsheet-based salary tracking for 10,000 employees across multiple countries.

**Status:** 🚧 Just started — repository scaffolding in progress.

**Submitted by:** Christelle Mamekem Ngueguim
**Contact:** mnchristelle@gmail.com · [LinkedIn](https://linkedin.com/in/christelle-mamekem-ngueguim) · [GitHub](https://github.com/christaime)

---

## 📌 What's Here Today

- Initial repository structure
- Documentation skeletons (`REQUIREMENTS.md`, `AI_WORKFLOW.md`)
- _(Nothing else yet — the build starts next.)_

---

## 🎯 Planned

The problem statement describes a single pain point: salary data for 10,000
employees across multiple countries is currently managed in Excel files. The
work is "tedious" — and the goal is to let the HR manager **manage this data
via a web app and answer questions about how the organization pays people.**

The plan is to replace the spreadsheet workflow with:

- **Employee salary data management** — CRUD, search, and filtering over 10,000 records
- **Multi-country salary dimensions** — grouping and comparison across countries
- **Analytics** — averages, distributions, top earners, and payroll cost by country and department
- **Seed data** — 10,000 employees for realistic testing

What the current spreadsheet process likely does poorly (and what the app will improve):

- **No single source of truth** — copies get emailed around, versions diverge
- **Manual entry is error-prone** — typed values, free-form fields, inconsistent formats
- **Querying requires manual work** — pivot tables, filters, and lookups for every question
- **Scale breaks down** — 10,000 rows with formulas becomes slow and fragile
- **No consistent multi-country view** — separate files per country, no unified picture

_(These will move into "What's Here Today" as they're built.)_

---

## 📁 Project Structure

```
salary-management-incubyte/
├── README.md
├── REQUIREMENTS.md
├── AI_WORKFLOW.md
└── docs/
├── architecture.md
├── adr/
└── diagrams/
```

_(Folders for `backend/` and `frontend/` will be added as the build progresses.)_

---

## 📬 Submission

Submitted in response to the Incubyte Software Craftsperson (Java/Angular II)
take-home assessment, October 2026.

For questions or clarifications, feel free to reach out:
**mnchristelle@gmail.com**
```