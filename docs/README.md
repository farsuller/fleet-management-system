# Fleet Management System — Documentation

This folder contains **Phase 0 planning outputs** that become the source of truth for implementation phases.

## Contents

- Architecture
  - `docs/architecture/bounded-contexts.md`
- Decisions (ADRs)
  - `docs/decisions/ADR-0001-packaging.md`
- API
  - `docs/api/conventions.md`
  - `docs/api/response-and-errors.md`
  - `docs/api/idempotency.md`
- Security
  - `docs/security/auth-and-rbac.md`
- Events
  - `docs/events/catalog-v1.md`
- Database
  - `docs/db/schema-blueprint.md`
- Observability
  - `docs/observability/standard.md`
- Deployment
  - `docs/deployment/local-dev.md`
  - `docs/deployment/eks-checklist.md`

## Ground rules

- Keep docs short, scannable, and example-driven.
- If the code and docs disagree, **update the docs or the code immediately** (outdated docs are worse than none).
