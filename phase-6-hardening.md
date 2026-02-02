# Phase 6 — Hardening

## Status

- Overall: **Not Started**

## Purpose

Improve correctness, reliability, and performance under real-world concurrency and load.

## Depends on

- Phases 1–5 (baseline functionality exists)

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Transaction boundaries review | Not Started | Ensure strong consistency for rentals/accounting; avoid partial writes |
| Concurrency strategy | Not Started | Locking/advisory locks where needed; avoid race conditions |
| Double-booking tests | Not Started | Prove DB exclusion constraint works under load |
| Kafka backpressure + consumer tuning | Not Started | Lag monitoring, retries, DLQ volume control |
| Redis usage audit | Not Started | TTLs defined; no source-of-truth usage; cache stampede mitigation |
| Rate limiting | Not Started | Apply 429 + headers; prevent brute force/resource exhaustion |
| Security hardening | Not Started | Auth/authz tests; least privilege; OWASP API Top 10 checklist |
| Observability completeness | Not Started | Golden signals metrics; tracing coverage; structured log fields |
| Performance tuning | Not Started | Index tuning, query plan improvements, N+1 elimination |
| Failure-mode testing | Not Started | Dependency outages, timeouts, partial failures, replay safety |

## Definition of Done (Phase 6)

- Concurrency correctness is demonstrated (especially rental overlap prevention).
- Kafka consumers handle retries/DLQs safely under load.
- Rate limiting, security checks, and observability are production-ready.

## Code impact (expected repo artifacts)

- **Code expected in this phase** (tests, tuning changes, resilience features).
- Suggested artifacts:
  - `src/test/kotlin/...` (concurrency/load-focused tests where feasible)
  - `docs/runbooks/` (failure scenarios, operational responses)

## References
- `fleet-management-plan.md`
- `skills/backend-development/SKILL.md`
- `skills/clean-code/SKILL.md`
- `skills/api-patterns/rate-limiting.md`
