# Phase 0 — Plan (requirements, dependencies, boundaries)

## Status

- Overall: **Completed**

## Purpose

Lock the scope, invariants, domain boundaries, and cross-cutting standards so implementation phases can move fast without re-deciding fundamentals.

## Depends on

- None

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Domain glossary | Completed | `docs/architecture/bounded-contexts.md` |
| Bounded-context map | Completed | `docs/architecture/bounded-contexts.md` |
| Invariants agreed | Completed | `docs/architecture/bounded-contexts.md` |
| Packaging decision | Completed | `docs/decisions/ADR-0001-packaging.md` |
| API standards | Completed | `docs/api/conventions.md`, `docs/api/response-and-errors.md` |
| Auth standards | Completed | `docs/security/auth-and-rbac.md` |
| Idempotency standards | Completed | `docs/api/idempotency.md` |
| Event catalog v1 | Completed | `docs/events/catalog-v1.md` |
| Persistence blueprint | Completed | `docs/db/schema-blueprint.md` (details in `fleet-management-plan.md`) |
| Observability baseline | Completed | `docs/observability/standard.md` |
| Local dev plan | Completed | `docs/deployment/local-dev.md` |
| EKS deployment checklist | Completed | `docs/deployment/eks-checklist.md` |

## Definition of Done (Phase 0)

- Phase 0 outputs exist as docs and are agreed.
- Key invariants and boundaries are unambiguous.
- Cross-cutting standards are written down in a way Phase 1 can implement directly.

## Code impact

- **No code required in Phase 0**: planning + documentation only.
- Optional repo artifacts (docs only):
  - `docs/architecture/`
  - `docs/api/`
  - `docs/events/`
  - `docs/decisions/` (ADRs)

## References

- `skills/backend-development/SKILL.md`
- `skills/clean-code/SKILL.md`
- `skills/api-patterns/rest.md`
- `skills/api-patterns/response.md`
- `skills/api-patterns/versioning.md`
- `skills/api-patterns/auth.md`
- `skills/api-patterns/documentation.md`
- `skills/api-patterns/rate-limiting.md`
