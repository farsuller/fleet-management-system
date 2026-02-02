# ADR-0001: Packaging (Modular Monolith vs Multi-service)

## Status

- Accepted

## Context

We need clear domain boundaries (vehicles, rentals, maintenance, users/staff, accounting, reporting) and the ability to evolve toward independently deployable services.

## Decision

Start with a **modular monolith** with strict domain modules:

- Separate domain modules/packages by bounded context.
- Enforce boundaries via module dependencies (no “reach across” persistence).
- Prefer API-first integration patterns (internal HTTP-like interfaces or explicit ports/adapters), so extracting a service later is straightforward.

## Rationale

- Faster delivery of end-to-end capability while the team is small.
- Easier local development/debugging.
- Still supports clean boundaries (hexagonal/clean architecture).

## Consequences

### Positive

- Single deployable early on.
- Consistent cross-cutting standards (auth, errors, observability).

### Negative / risks

- Risk of boundary erosion (shared tables, “just call that repository”).
- Scaling teams may require stronger ownership enforcement.

## Guardrails (must follow)

- **No shared database schema across domains** inside the code. If a module needs data from another domain, use:
  - an API/port
  - an event projection/read model
- Domain modules own their migrations and persistence adapters.
- Avoid “god” shared modules. Shared libraries should be narrow and infrastructure-focused (logging, auth primitives, error format).
