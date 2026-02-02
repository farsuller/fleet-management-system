# Phase 1 — Architecture skeleton

## Status

- Overall: **Completed**

## Purpose

Establish the backend foundation (Ktor service template, clean boundaries, cross-cutting standards) so later phases can add schemas, APIs, and eventing consistently.

## Depends on

- Phase 0 plan outputs (domain boundaries, invariants, standards)

## Inputs / constraints

- Kotlin + **Ktor** + coroutines
- AuthN/AuthZ: OAuth 2.0 + JWT, RBAC via JWT claims
- PostgreSQL source of truth; Redis cache/locks only; Kafka at-least-once
- Observability: JSON logs, health endpoints, Micrometer, OpenTelemetry

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Choose service packaging (modular monolith vs multi-service) | Completed | Default: **Modular Monolith** using **Clean Architecture** (Domain, Application, Infrastructure layers internal to modules) |
| Define module boundaries (vehicles/rentals/maintenance/users/accounting) | Completed | Each module owns its business rules (Domain) and persistence adapters (Infrastructure) |
| Ktor HTTP baseline (routing + pipeline) | Completed | Main.kt exists, acts as the Web Adapter (Infrastructure) |
| Standard response + error envelope | Completed | Consistent format; no internal error leakage; includes requestId |
| JWT auth + RBAC middleware | Completed | Standard claims + permission mapping; 401 vs 403 behavior |
| Validation approach | Completed | API-boundary validation + domain validation; fail-fast |
| Database access baseline | Completed | Pick Exposed/jOOQ/Hibernate per module; explicit transaction boundaries |
| Migrations baseline | Completed | Flyway/Liquibase conventions; per-module migrations strategy |
| Kafka baseline | Completed | Outbox publishing contract; consumer idempotency contract; retry/DLQ policy |
| Redis baseline | Completed | TTL policy; cache key conventions; lock conventions (if used) |
| Observability baseline | Completed | JSON logs, metrics registry, tracing propagation, health endpoints |
| Local dev baseline | Completed | Docker Compose/Testcontainers for Postgres/Kafka/Redis; env override strategy |

## Definition of Done (Phase 1)

- [x] A runnable Ktor backend skeleton exists (even if endpoints are stubbed).
- [x] Auth, error handling, validation, and observability are wired consistently.
- [x] Database migrations framework is ready for Phase 2.
- [x] Kafka/Redis usage conventions are documented and enforceable.

## Code impact (expected repo artifacts)

- **Code expected in this phase** (foundation/skeleton).
- Suggested artifacts (adjust to chosen packaging):
  - `src/main/kotlin/...` (Ktor app, routing, auth, error handling)
  - `src/main/resources/application.yaml` (env overrides supported)
  - `db/migration/` (migration framework wiring; schema comes in Phase 2)
  - `docs/api/` (response/error envelope, auth claims, idempotency header)

## References
- `fleet-management-plan.md`
- `skills/backend-development/SKILL.md`
- `skills/clean-code/SKILL.md`
- `skills/api-patterns/rest.md`
- `skills/api-patterns/response.md`
