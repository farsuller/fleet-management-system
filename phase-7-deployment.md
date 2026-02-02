# Phase 7 — Deployment (Render)

## Status

- Overall: **Not Started**

## Purpose

Make the system runnable locally and deployable to **Render** with safe configuration, health checks, and observability. 
We prioritize Render for MVP speed and simplicity, while keeping EKS notes for future scaling.

## Depends on

- Phase 1 baseline (health endpoints, config strategy)  
- Phases 2–6 as applicable (services exist to deploy)

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Local dev: Docker Compose | Not Started | Postgres/Redis; sensible defaults; env overrides |
| Local dev: Testcontainers | Not Started | Use for integration tests and parity; document usage |
| Containerization | Not Started | Build images for services/modules; Dockerfile strategy for Render |
| Render Web Service | Not Started | Create service, connect GitHub, enable auto-deploy |
| Render Env Vars | Not Started | Configure `DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, `PORT` in dashboard |
| Render Postgres | Not Started | Provision instance; auto-run Flyway migrations on startup |
| Render Redis | Not Started | Cache, rate limiting, ephemeral state; define TTLs |
| Health checks | Not Started | `/health` endpoint wired to Render health checks |
| Background jobs | Not Started | Single-instance jobs only; coroutine schedulers or Quartz |
| Observability | Not Started | Structured logs (JSON); viewable in Render dashboard |
| CI/CD expectations | Not Started | Build, test, scan, deploy via Render auto-deploy |
| Kubernetes/EKS notes | Optional | Future reference only; see `docs/deployment/eks-checklist.md` |

## Deployment Guide (Render)

### 1. Target Architecture
- **One Web Service**: Kotlin + Ktor (modular monolith).
- **Managed Postgres**: Primary datastore.
- **Managed Redis**: Cache & ephemeral state.
- **No Kafka**: Intentionally omitted for MVP.

### 2. Build Strategy
**Recommended**: Docker-based build.
- Use a `Dockerfile` to produce a minimal runtime image (JDK 21 slim).
- Avoid relying on Render native builds for JVM services (often slower/custom).

### 3. Runtime Configuration
- Ktor must bind to `0.0.0.0`.
- Port must use `PORT` env var (Render injects this, usually `10000`).

### 4. Database & Migrations
- Use **Render Postgres** (internal connection string).
- Run Flyway migrations at **application startup** (safest for single-instance MVP).
- *Warning*: Avoid concurrent migrations if you scale to >1 instance.

### 5. Background Jobs
- Allowed only under **single-instance** deployment.
- Use coroutine-based schedulers or Quartz (non-clustered).
- *Warning*: Do not enable multiple instances yet.

### 6. Observability
- Emit structured (JSON) logs to stdout.
- Use Render logs dashboard.
- Optional: Export traces/metrics via OpenTelemetry if needed later.

## Definition of Done (Phase 7)

- [ ] Local environment can run the system reliably (`docker-compose up`).
- [ ] **Render deployment is fully operational**:
  - [ ] Web Service connected to GitHub & auto-deploys.
  - [ ] Env vars (`DATABASE_URL`, `REDIS_URL`, `JWT_...`) configured safely.
  - [ ] Postgres + Redis provisioned and connected.
  - [ ] Health checks (`/health`) passing.
  - [ ] Background jobs running safely (single instance).
- [ ] Production configuration is externalized (no hardcoded secrets).
- [ ] EKS/Kubernetes manifests are optional/deferred.

## Code impact (expected repo artifacts)

- `Dockerfile` (for Render)
- `docker-compose.yml` (for local dev)
- `docs/deployment/render.md` (this guide)

## References

- `fleet-management-plan.md`
- `skills/backend-development/SKILL.md`
- `render_deployment_guide.md`
