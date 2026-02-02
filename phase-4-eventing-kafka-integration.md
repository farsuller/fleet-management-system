# Phase 4 — Eventing (Kafka) + integration

## Status

- Overall: **Not Started**

## Purpose

Implement the **Messaging Infrastructure** to publish and consume Domain Events. This supports loose coupling between modules without distributed transactions.

## Depends on

- Phase 1 architecture skeleton (Kafka/observability standards)
- Phase 2 schema v1 (outbox/inbox tables)
- Phase 3 API v1 (events emitted by state changes)

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Event catalog v1 | Not Started | Event names, payload schema, versioning, ownership, consumers |
| Topic strategy | Not Started | Naming, partitions, keys, retention; per-domain topics vs shared |
| Outbox publisher | Not Started | Transactional outbox; publish with keys; mark `published_at` |
| Consumer baseline | Not Started | Coroutine-based consumers; explicit offset management |
| Idempotency (inbox) | Not Started | `inbox_processed_messages` and deterministic de-dupe keying |
| Retry policy | Not Started | Backoff, max attempts, poison message handling |
| DLQ handling | Not Started | DLQ topic naming, payload, alerting, replay process |
| Event handlers per domain | Not Started | Projectors/read models, cross-domain reactions, reconciliation |
| Observability for messaging | Not Started | Trace context propagation, structured logs, metrics (lag, retries, DLQ) |

## Definition of Done (Phase 4)

- Events are produced via outbox and consumed idempotently.
- Retry + DLQ paths are implemented and documented (including replay).
- Offsets are managed explicitly; handlers tolerate duplicates.

## Code impact (expected repo artifacts)

- **Code expected in this phase** (Kafka producers/consumers, outbox/inbox, handlers).
- Suggested artifacts:
  - `src/main/kotlin/.../messaging/` (producer/consumer, serializers)
  - `src/main/kotlin/.../outbox/` + `.../inbox/`
  - `docs/events/` (catalog, topics, retry/DLQ, replay runbook)

## References
- `fleet-management-plan.md`
- `skills/backend-development/SKILL.md`
- `skills/clean-code/SKILL.md`
