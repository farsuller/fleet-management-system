# Schema Blueprint (v1 summary)

PostgreSQL is the **source of truth**. Schemas must enforce correctness with constraints and indexes.

For detailed table layouts, see `fleet-management-plan.md` (Schemas section).

## Global conventions

- PKs: `UUID`
- Audit timestamps: `created_at`, `updated_at` (`TIMESTAMPTZ`)
- Optional soft delete: `deleted_at`
- Money: `amount_cents` + `currency` (no floats)
- Status fields: `TEXT` with `CHECK` constraints (or enums if explicitly chosen)

## Domain highlights (critical constraints)

### Rentals: prevent double-booking

- Store a rental period as `tstzrange(start_at, end_at, '[)')`
- Add GiST exclusion constraint:
  - `vehicle_id WITH =` and `rental_period WITH &&`
  - apply only when status in `('reserved','active')`

### Accounting: idempotent postings

- `journal_entries` has a unique key:
  - `(external_ref_type, external_ref_id)`
- Journal lines must balance (debits == credits) per entry (enforced in app logic; optionally via DB constraints/deferrable checks if chosen).

### Messaging integration tables (per module/service)

- `outbox_events` (transactional outbox)
- `inbox_processed_messages` (idempotent consumption)

## Indexing principles

- Index by primary query paths (not “everything”).
- Verify with query plans once endpoints exist.
- Avoid N+1 patterns in application access.
