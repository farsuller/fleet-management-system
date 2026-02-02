# Phase 5 — Reporting and accounting correctness

## Status

- Overall: **Not Started**

## Purpose

Make reporting and accounting **reproducible and auditable** using immutable facts (ledger postings) and derived read models/snapshots.

## Depends on

- Phase 2 schema v1 (ledger tables and invariants)
- Phase 3 API v1 (posting and read APIs)
- Phase 4 eventing (optional but recommended for projections)

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Define accounting rules | Not Started | Posting rules per business event (rental activation/completion, maintenance cost, payment capture) |
| Enforce idempotent postings | Not Started | Unique external reference per journal entry; safe retries |
| Read models for reports | Not Started | Materialized views or query projections; never overwrite facts |
| Report snapshot strategy | Not Started | Append-only snapshots; parameters captured; reproducible outputs |
| Reconciliation checks | Not Started | Detect drifts between rentals/payments and ledger; alerting strategy |
| Performance plan | Not Started | Indexing, query plans, caching policy (if any) |

## Definition of Done (Phase 5)

- Financial reports are derived from immutable facts and can be regenerated.
- Ledger postings are idempotent and auditable.
- Optional snapshots are append-only and traceable to input parameters.

## Code impact (expected repo artifacts)

- **Code expected in this phase** (ledger posting logic + report queries/snapshots).
- Suggested artifacts:
  - `src/main/kotlin/.../accounting/` (posting rules, validation)
  - `src/main/kotlin/.../reporting/` (queries, snapshot generation)
  - `docs/accounting/` (posting rules, reconciliation, report definitions)

## References
- `fleet-management-plan.md`
- `skills/backend-development/SKILL.md`
- `skills/clean-code/SKILL.md`
