# Phase 2 — PostgreSQL schema v1 (source of truth)

## Status

- Overall: **Not Started**

## Purpose

Create the **Persistence Infrastructure** (production-grade schemas) per domain. This implements the data access requirements defined by the Domain layer, enforcing correctness with constraints and indexes.

## Depends on

- Phase 1 architecture skeleton (migration framework, DB access baseline)

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Confirm DB conventions | Not Started | **Infrastructure Layer**: UUID PKs, TIMESTAMPTZ audit fields, money as cents+currency (implements Domain Repository contracts) |
| Choose migration tool + conventions | Not Started | Flyway or Liquibase; naming/versioning rules |
| Users/Staff schema | Not Started | Users, roles/permissions, staff profiles (or external IdP integration boundaries) |
| Vehicles schema | Not Started | Vehicles, odometer readings; enforce non-decreasing mileage strategy |
| Rentals schema | Not Started | Rentals + charges + payments + locations (as needed) |
| Double-booking prevention | Not Started | Exclusion constraint on `vehicle_id` + `tstzrange` for `reserved|active` |
| Maintenance schema | Not Started | Jobs, parts used, costs |
| Accounting schema | Not Started | Accounts + journal entries/lines; enforce idempotent posting via unique external reference |
| Integration tables | Not Started | `outbox_events` + `inbox_processed_messages` (per service/module) |
| Indexing + query plan review | Not Started | Add indexes for expected read paths; avoid N+1 patterns |

## Definition of Done (Phase 2)

- All domain schemas exist as migrations and can be applied cleanly to a fresh DB.
- Critical invariants are enforced at the DB level where appropriate (double-booking, idempotent postings).
- Indexes exist for primary query paths; constraints prevent invalid data.

## Code impact (expected repo artifacts)

- **Code expected in this phase** (migrations + persistence wiring).
- Suggested artifacts:
  - `db/migration/V*__*.sql` (or Liquibase changelogs)
  - `src/main/kotlin/.../persistence/` (tables/mappings, transactions)
  - `docs/db/` (schema notes + invariants enforced by constraints)

## References

- `fleet-management-plan.md` (Schemas section)
- `skills/backend-development/SKILL.md`
- `skills/clean-code/SKILL.md`
