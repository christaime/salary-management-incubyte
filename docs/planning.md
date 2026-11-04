# Planning

> How the work was planned, what state each milestone is in, and why some
> things were deliberately excluded. The issue-level record lives on GitHub:
> <https://github.com/christaime/salary-management-incubyte/milestones>

## Approach

The system was planned as twelve milestones, each a coherent slice of the
domain rather than a technical layer. Every milestone starts from a written
decision (an ADR or a use case) and ends with code that compiles, tests that
pass, and a changelog entry. Milestones were built in order — the domain
before the adapters, the adapters before the API, the API before the UI — so
that every later milestone could assume the earlier ones were stable.

The milestones are a planning tool, not a delivery contract. Two of them
(M8, M9) shipped most of what they planned and deliberately stopped short of
the rest. One (M12) is partially done. The rest are complete.

## Milestones

| M | Focus | State |
| :--- | :--- | :--- |
| M1 | Domain models, repository ports, enums, value objects, and their tests | 14/14 ✅ |
| M2 | JPA entities, Flyway migration, mappers, adapters, integration tests | 8/8 ✅ |
| M3 | Deterministic seed: 10,000 employees, mixed-frequency periods over ~6 months | 5/5 ✅ |
| M4 | Build snapshots from live payroll summaries; warnings and blockers | 4/4 ✅ |
| M5 | Aggregate report — DTOs, service, controller, error handling | 7/7 ✅ |
| M6 | Angular report page — date range, group keys, results table | 5/5 ✅ |
| M7 | Single JAR serves frontend and API; CI workflow; README update | 3/3 ✅ |
| M8 | Ranking and comparison reports | 2/4 ⚠️ Ranking done; comparison excluded |
| M9 | Employee and assignment CRUD | 5/6 ⚠️ Employee detail page excluded |
| M10 | Payroll recording, validation, payroll list | 6/6 ✅ |
| M11 | Payroll period list, create, edit, settle (close) flow | 4/4 ✅ |
| M12 | Video demo, ADRs, documentation updates, deployment | 3/5 ⚠️ Video, ADRs 006–008 pending |

## Exclusions

The open issues on M8, M9, and M12 are the concrete record of what was left
out, and why. The user-facing consequences are in **Known Limitations** in
the README. The reasoning behind each exclusion:

- **Comparison report (M8).** The aggregate report already supports grouping
  by any dimension and filtering by selection. A comparison report would
  answer "how does department X compare to department Y on a fixed metric"
  — a useful query, but one the aggregate report answers adequately with a
  `DEPARTMENT` group key and a selection filter. The `ReportKind.COMPARISON`
  enum value is a placeholder so the shape of a future report is not lost.
  Not worth building in M8.

- **Employee detail page (M9).** The employee edit form is both the view
  and the edit surface. A separate read-only detail page would duplicate
  every field for the sole purpose of being not-editable. If a future use
  case needs a read-only view (e.g. a printable employee record), it gets
  built then.

- **Video demo (M12).** A demo is a recording artifact, not a code artifact.
  The README and the deployed instance serve the same purpose. Not built.

- **ADRs 006–008 (M12).** The three ADRs cover the snapshot ranking, the
  monthly aggregate report, and the currency conversion — decisions that
  are documented across `docs/architecture.md` and `docs/adr/005`. Their
  consolidation into standalone ADRs is a documentation cleanup, not a gap
  in reasoning. Partially done.

## What moved during the build

- **M11 was scoped, then built.** The original plan listed it as out of
  scope (periods seeded only). During the assessment it became clear that a
  period-management feature was required for the snapshot lifecycle to have
  an owner; the feature shipped with the two design decisions captured in
  `docs/adr/009` and `docs/adr/010`.

- **The calendar-month rule for MONTHLY periods was relaxed.** The original
  design required a MONTHLY period to align with a calendar month. Real HR
  workflows (mid-month close, 4-4-5 cycles) made that too restrictive, and
  the rule was relaxed to a 28–31 day span. The reasoning is in `009`.

- **The previous-period-closed gate on creation was dropped.** The original
  design required the previous period of the same frequency to be closed
  before a new one could be created. HR practice creates periods for the
  year ahead, months before any close, and the report engine does not
  depend on the gate. The shipped rule is name + overlap only. Reasoning in
  `009`.

- **The bi-weekly chain rule replaced independent windows.** The original
  plan modelled each monthly cascade independently. Overlap and drift made
  that unworkable the moment a previous month's chain ended mid-next-month.
  Chain continuation replaced it. Reasoning in `009`.

## Known non-goals

Beyond the milestone exclusions, the following are stated non-goals of the
system — not "not done yet", but deliberately out of scope, recorded here so
future contributors do not mistake the omission for an oversight:

- **Payroll execution.** No tax computation, gross-to-net, or payslips.
  Payroll is recorded, not computed. (See ADR 002.)
- **Authentication and authorization.** A single trusted HR Manager user.
  (Assessment framing.)
- **Historical currency rates.** Conversion uses the current rate. Changing
  the reference currency would require a full re-conversion. (See ADR 001.)
- **Audit trail, backup, disaster recovery.** Out of scope for the
  assessment.
- **Excel import.** The seed replaces it.

The full list of user-facing limitations is in **Known Limitations** in the
README.