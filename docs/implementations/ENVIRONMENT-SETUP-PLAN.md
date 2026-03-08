# Environment Setup Implementation Plan

**Version**: 1.0  
**Date**: 2026-03-08  
**Dev URL**: `http://127.0.0.1:8080`  
**Prod URL**: `https://fleet-management-api-8tli.onrender.com`

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Development Environment](#2-development-environment)
   - [2.1 Prerequisites](#21-prerequisites)
   - [2.2 Local Services (Docker Compose)](#22-local-services-docker-compose)
   - [2.3 Environment Variables](#23-environment-variables)
   - [2.4 Running the Application](#24-running-the-application)
   - [2.5 Running Tests](#25-running-tests)
   - [2.6 Developer Tooling](#26-developer-tooling)
   - [2.7 Common Issues & Fixes](#27-common-issues--fixes)
3. [Production Environment](#3-production-environment)
   - [3.1 Infrastructure Overview](#31-infrastructure-overview)
   - [3.2 Required Secrets & Env Vars](#32-required-secrets--env-vars)
   - [3.3 Deployment Process](#33-deployment-process)
   - [3.4 Database Migrations in Production](#34-database-migrations-in-production)
   - [3.5 Health & Observability](#35-health--observability)
   - [3.6 Known Limitations (Free Tier)](#36-known-limitations-free-tier)
4. [Environment Comparison Matrix](#4-environment-comparison-matrix)
5. [CI/CD Pipeline](#5-cicd-pipeline)
6. [Security Checklist](#6-security-checklist)

---

## 1. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                       DEVELOPMENT                                     │
│                                                                       │
│  ./gradlew run                          http://127.0.0.1:8080        │
│       │                                                               │
│       ▼                                                               │
│  Ktor (Netty) ──── HikariCP ──── PostgreSQL (Docker, port 5435)     │
│                │                                                      │
│                └── Jedis ──── Redis (Docker, port 6379)              │
│                                                                       │
│  Docker Compose: postgis/postgis:15-3.3-alpine + redis:7-alpine     │
└──────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────┐
│                        PRODUCTION                                     │
│                                                                       │
│  Render Web Service (Docker)     https://fleet-management-api-       │
│       │                                  8tli.onrender.com           │
│       ▼                                                               │
│  Ktor (Netty) ──── HikariCP ──── Supabase PostgreSQL (DATABASE_URL) │
│                │                                                      │
│                └── Jedis ──── Render Managed Redis (REDIS_URL)       │
│                                                                       │
│  Auto-deploy: GitHub push → GitHub Actions CI → Render CD           │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 2. Development Environment

### 2.1 Prerequisites

| Tool | Version | Installation |
|------|---------|-------------|
| **JDK** | 21+ | [Eclipse Temurin 21](https://adoptium.net/) |
| **Docker Desktop** | Latest | [docker.com](https://www.docker.com/products/docker-desktop/) |
| **Git** | 2.x+ | System package manager |
| **IntelliJ IDEA** | Recommended | With Kotlin plugin |

Verify installation:
```powershell
java -version          # Should show 21.x
docker --version       # Should show 20+ or 24+
git --version
```

---

### 2.2 Local Services (Docker Compose)

The `docker-compose.yml` in the project root starts all required backing services.

```yaml
# Current docker-compose.yml services:
#   - PostgreSQL (PostGIS) → localhost:5435
#   - Redis 7              → localhost:6379
```

**Start services:**
```powershell
docker-compose up -d
```

**Verify services are running:**
```powershell
docker ps
# Expect: fleet_postgres (port 5435) and fleet_redis (port 6379)
```

**Stop services:**
```powershell
docker-compose down
```

**Reset database (wipe all data):**
```powershell
docker-compose down -v     # -v removes named volumes
docker-compose up -d
```

> ⚠️ **Important**: The local PostgreSQL is mapped to host port **5435**, NOT 5432.
> This avoids conflicts with any existing local PostgreSQL installation.

---

### 2.3 Environment Variables

The `application.yaml` provides all defaults for local development. **No `.env` file is needed** to run locally.

#### Effective Local Defaults (from `application.yaml`)

| Variable | Default | Notes |
|----------|---------|-------|
| `PORT` | `8080` | Server binds to `0.0.0.0:8080` |
| `DATABASE_URL` | `jdbc:postgresql://127.0.0.1:5435/fleet_db` | Matches docker-compose |
| `DB_USER` | `fleet_user` | Matches docker-compose |
| `DB_PASSWORD` | `secret_123` | Matches docker-compose |
| `DB_POOL_SIZE` | `10` | HikariCP pool size |
| `JWT_SECRET` | `change-me-in-production-use-env-var-min-64-chars` | ⚠️ Dev only, not secure |
| `JWT_ISSUER` | `http://0.0.0.0:8080/` | |
| `JWT_AUDIENCE` | `http://0.0.0.0:8080/` | |
| `JWT_EXPIRATION` | `3600000` | 1 hour in ms |
| `REDIS_URL` | `redis://localhost:6379` | Matches docker-compose |
| `REDIS_ENABLED` | `true` | Set to `false` to disable Redis caching |

#### Override for Local Testing (Optional)

Create a local `.env` file (git-ignored) only if you need to override specific values:
```bash
# .env (never commit this)
PORT=8081
REDIS_ENABLED=false
```

To run with a `.env` file using Gradle:
```powershell
./gradlew run -Ddotenv=.env   # (if dotenv support is configured)
# OR set PowerShell environment variables directly:
$env:PORT = "8081"; ./gradlew run
```

---

### 2.4 Running the Application

**Standard run (Gradle):**
```powershell
# Windows
.\gradlew.bat run

# After startup, the server is at:
# http://127.0.0.1:8080
```

**Verify startup:**
```powershell
# Health check
curl http://127.0.0.1:8080/health
# Expected: {"success":true,"data":{"status":"OK"},"requestId":"..."}

# API info
curl http://127.0.0.1:8080/
# Expected: {"success":true,"data":"Fleet Management API v1",...}
```

**Swagger UI (Interactive API Docs):**
```
http://127.0.0.1:8080/swagger
```

**Prometheus Metrics:**
```
http://127.0.0.1:8080/metrics
```

**Database Migrations**: Flyway runs automatically on startup. Check logs for:
```
Successfully applied N migrations to schema "public"
```

---

### 2.5 Running Tests

#### Unit Tests (no Docker needed)
```powershell
.\gradlew.bat test
```

Unit tests use either:
- **MockK** for use case tests (no DB)
- **H2 in-memory** for `ApplicationTest.kt` (basic endpoints only)

#### Integration Tests (requires Docker)
> ⚠️ **Note**: As of 2026-03-08, full integration tests are not yet implemented per the Integration Test Plan. Currently only `ApplicationTest.kt` (H2-based, health endpoint only) runs.

When integration tests are added:
```powershell
# Ensure Docker Desktop is running first
.\gradlew.bat integrationTest
```

Testcontainers will automatically start a PostgreSQL container for each test run.

#### Spatial Tests
```powershell
# PostGISAdapterTest is currently @Disabled
# To run manually (requires Docker):
.\gradlew.bat test --tests "com.solodev.fleet.PostGISAdapterTest" -Dpostgis.enabled=true
```

#### Test Reports
After running tests, HTML report is at:
```
build/reports/tests/test/index.html
```

---

### 2.6 Developer Tooling

#### Build Fat JAR (for Docker testing locally)
```powershell
.\gradlew.bat buildFatJar
# Output: build/libs/fleet-management-*-all.jar
```

#### Build and Run Docker Locally
```powershell
docker build -t fleet-management:local .
docker run -p 8080:8080 `
  -e DATABASE_URL="jdbc:postgresql://host.docker.internal:5435/fleet_db" `
  -e DB_USER="fleet_user" `
  -e DB_PASSWORD="secret_123" `
  -e JWT_SECRET="test-secret-at-least-64-bytes-long-for-local-docker-testing" `
  -e JWT_ISSUER="local-test" `
  -e JWT_AUDIENCE="local-test" `
  -e REDIS_ENABLED="false" `
  fleet-management:local
```

> ⚠️ Use `host.docker.internal` (not `127.0.0.1`) to connect Docker container to host services.

#### Connect to Local Database
```powershell
# Using Docker exec
docker exec -it fleet_postgres psql -U fleet_user -d fleet_db

# Using any DB client (e.g., DBeaver, DataGrip):
# Host: 127.0.0.1
# Port: 5435
# Database: fleet_db
# User: fleet_user
# Password: secret_123
```

---

### 2.7 Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| `Connection refused` on startup | Docker Compose not running | `docker-compose up -d` |
| Port 5435 already in use | Another service on 5435 | `docker-compose down` then change port in `docker-compose.yml` |
| `Flyway migration failed` | DB schema mismatch | `docker-compose down -v && docker-compose up -d` (wipes DB) |
| Redis connection error | Redis container not running | `docker-compose up -d redis` |
| `JWT_SECRET too short` | Secret < 64 chars | Default is safe for dev; never change to something shorter |
| H2 test failures | H2 doesn't support all PostgreSQL features (e.g., PostGIS) | Expected — PostGIS tests are `@Disabled` in H2 mode |

---

## 3. Production Environment

**Live URL**: `https://fleet-management-api-8tli.onrender.com`

### 3.1 Infrastructure Overview

| Component | Provider | Plan | Notes |
|-----------|----------|------|-------|
| **Web Service** | [Render](https://render.com) | Free (can upgrade to Starter $7/mo) | Docker-based, auto-deploy |
| **Database** | [Supabase](https://supabase.com) | Free tier | PostgreSQL 15 + PostGIS |
| **Redis** | Render Managed Redis | Free tier | Connected via `REDIS_URL` |
| **CI/CD** | GitHub Actions | Free (public repo) | Build, test, notify |

> ⚠️ **Free Tier Warning**: The Render free web service **spins down after 15 minutes of inactivity**. The first request after spin-down takes 30–60 seconds (cold start). Upgrade to Starter ($7/mo) for always-on behavior in production.

---

### 3.2 Required Secrets & Env Vars

All environment variables are configured in Render's dashboard or auto-generated. The following must be set:

#### 🔴 Critical — Must Set Manually in Render Dashboard

| Variable | Where to Set | Value |
|----------|-------------|-------|
| `DATABASE_URL` | Render Dashboard → Environment | Supabase connection string (JDBC format) |
| `DB_USER` | Render Dashboard → Environment | Supabase DB user |
| `DB_PASSWORD` | Render Dashboard → Environment | Supabase DB password |

**Supabase JDBC URL format:**
```
jdbc:postgresql://db.<project-ref>.supabase.co:5432/<database>?sslmode=require
```

#### 🟡 Auto-Configured by render.yaml

| Variable | Source | Value |
|----------|--------|-------|
| `PORT` | Render injected | `10000` |
| `REDIS_URL` | Render Redis service | Auto-linked |
| `JWT_SECRET` | `generateValue: true` | Render auto-generates 64+ char random secret |
| `JWT_ISSUER` | render.yaml | `fleet-management-api` |
| `JWT_AUDIENCE` | render.yaml | `fleet-management-users` |
| `JWT_EXPIRATION` | render.yaml | `3600000` (1 hour) |
| `APP_ENV` | render.yaml | `production` |
| `DB_POOL_SIZE` | render.yaml | `10` |
| `LOG_LEVEL` | render.yaml | `INFO` |

---

### 3.3 Deployment Process

#### Step 1: Initial Setup (One-time)

```bash
# 1. Push render.yaml to your GitHub repo
git add render.yaml Dockerfile
git commit -m "chore: add deployment configuration"
git push origin main

# 2. In Render Dashboard:
#    - Connect GitHub repository
#    - Create New > Blueprint (uses render.yaml)
#    - Set DATABASE_URL, DB_USER, DB_PASSWORD from Supabase

# 3. Render will:
#    - Build Docker image from Dockerfile
#    - Run Flyway migrations on startup
#    - Start the web service
```

#### Step 2: Ongoing Deployments (Automatic)

Every push to `main` triggers the CI/CD pipeline:
```
git push origin main
    │
    ▼
GitHub Actions (CI):
    1. Runs unit tests (./gradlew test)
    2. Builds Fat JAR
    3. Builds Docker image
    4. Security scan (Trivy)
    │
    ▼ (if all CI passes)
Render (CD):
    1. Detects push to main
    2. Builds Docker image (from Dockerfile)
    3. Flyway runs migrations on startup
    4. New deployment goes live
    5. Zero-downtime swap (when on paid plan)
```

#### Step 3: Manual Deployment (Override)

If you need to force-redeploy without a code change:
1. Render Dashboard → Your service → Manual Deploy → Deploy Latest Commit

Or trigger via Render API:
```bash
curl -X POST "https://api.render.com/v1/services/<SERVICE_ID>/deploys" \
  -H "Authorization: Bearer <RENDER_API_KEY>"
```

---

### 3.4 Database Migrations in Production

**Migrations run automatically** on every application startup via Flyway configured in `Databases.kt`.

#### Migration Strategy
- Flyway scans `src/main/resources/db/migration/`
- Applies only new migrations (checksum-validated)
- Baseline: V001 to V019 (with V016 intentionally skipped)
- Order: sequential, no gaps allowed (V016 gap must remain — do not fill it)

#### Before Pushing Schema Changes
1. **Test migration locally** with `docker-compose down -v && docker-compose up -d && ./gradlew run`
2. Verify Flyway log shows "Successfully applied"
3. Push to GitHub — Render will apply the migration on next deploy

#### Rollback (Manual)
Flyway does not support automatic rollbacks. For rollback:
1. Write a new migration `V0XX__rollback_change.sql`
2. Push to main — it will be applied forward

---

### 3.5 Health & Observability

#### Health Check
```bash
curl https://fleet-management-api-8tli.onrender.com/health
# Expected: {"success":true,"data":{"status":"OK"},...}
```

Render also checks `/health` every 30 seconds (configured in `render.yaml`):
```yaml
healthCheckPath: /health
```

#### Logs
View structured JSON logs in Render Dashboard:
- Render Dashboard → Your Service → Logs
- All logs include `requestId` for correlation

#### Metrics
```
https://fleet-management-api-8tli.onrender.com/metrics
```
Prometheus-compatible metrics exposed at `/metrics` (Micrometer).

> ⚠️ Consider adding `/metrics` to an IP-allowlist or behind auth in production to prevent information disclosure.

#### Swagger UI (Production)
```
https://fleet-management-api-8tli.onrender.com/swagger
```

---

### 3.6 Known Limitations (Free Tier)

| Limitation | Impact | Mitigation |
|-----------|--------|-----------|
| **Web Service spins down** after 15 min inactivity | 30-60s cold start delay | Upgrade to Starter $7/mo for always-on |
| **750 free hours/month** cap | ~31 days = 744 hours — barely enough | Use only for dev/demo; upgrade for production |
| **Shared CPU** | Slower response under load | Upgrade to Standard $25/mo for dedicated CPU |
| **Redis free tier** limits | Limited memory | Monitor `redis-cli info memory` |
| **Supabase free tier** pauses if inactive 7 days | DB becomes unavailable | Use a keep-alive cron or upgrade |

---

## 4. Environment Comparison Matrix

| Aspect | Development | Production |
|--------|------------|-----------|
| **URL** | `http://127.0.0.1:8080` | `https://fleet-management-api-8tli.onrender.com` |
| **Port** | 8080 | 10000 (Render injects) |
| **Database** | PostgreSQL via Docker, port 5435 | Supabase PostgreSQL (managed, SSL) |
| **Redis** | Docker container, port 6379 | Render Managed Redis |
| **DB Password** | `secret_123` (hardcoded OK for dev) | From Render Dashboard secret |
| **JWT Secret** | Default placeholder (dev only) | Auto-generated by Render (64+ chars) |
| **Migrations** | Auto-run on `./gradlew run` | Auto-run on Render deploy |
| **Redis Enabled** | `true` (default `application.yaml`) | `true` (via `REDIS_URL`) |
| **Log Format** | JSON (Logstash encoder) | JSON (Logstash encoder) |
| **Swagger UI** | `http://127.0.0.1:8080/swagger` | `https://...onrender.com/swagger` |
| **Metrics** | `http://127.0.0.1:8080/metrics` | `https://...onrender.com/metrics` |
| **SSL/TLS** | No (HTTP) | Yes (Render provides HTTPS automatically) |
| **Deployment** | `./gradlew.bat run` | GitHub push → Auto-deploy |
| **Test DB** | Docker, wiped on `down -v` | **Never wipe manually** — migrants only |
| **Cold Start** | Instant (local JVM) | 30-60s on free tier after inactivity |

---

## 5. CI/CD Pipeline

```
┌───────────────────────────────────────────────────────────────────────┐
│ git push origin main                                                  │
└───────────────────────────┬───────────────────────────────────────────┘
                            │
                            ▼
┌───────────────────────────────────────────────────────────────────────┐
│ GitHub Actions                                                         │
│                                                                        │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────────┐ │
│  │ Job 1: Test     │  │ Job 2: Quality   │  │ Job 3: Security      │ │
│  │ ─────────────── │  │ ──────────────── │  │ ──────────────────── │ │
│  │ ./gradlew test  │  │ detekt (linting) │  │ Trivy scan (Docker)  │ │
│  │ (unit tests)    │  │ ktlint (format)  │  │ Upload to GitHub     │ │
│  └────────┬────────┘  └────────┬─────────┘  └──────────────────────┘ │
│           │                   │                                        │
│           └─────────┬─────────┘                                        │
│                     ▼                                                   │
│  ┌──────────────────────────────────────┐                              │
│  │ Job 4: Build Docker Image            │                              │
│  │ docker build -t fleet-management .   │                              │
│  └──────────────────────────────────────┘                              │
└────────────────────────────┬──────────────────────────────────────────┘
                             │ (all jobs pass)
                             ▼
┌───────────────────────────────────────────────────────────────────────┐
│ Render CD (auto-deploy on main push)                                  │
│                                                                        │
│  1. Detect GitHub push                                                 │
│  2. Build Docker image (Render's own build)                           │
│  3. On startup: Flyway applies new migrations                         │
│  4. Health check passes → traffic switched to new instance            │
└───────────────────────────────────────────────────────────────────────┘
```

---

## 6. Security Checklist

### Development
- [ ] Never commit `.env` files — add to `.gitignore`
- [ ] Never use production `DATABASE_URL` locally
- [ ] Default JWT secret is for dev only — do not reuse in staging/prod
- [ ] Docker volumes (`postgres_data`, `redis_data`) contain no production data

### Production
- [ ] `JWT_SECRET` is Render auto-generated (64+ chars) — never manually set a weak value
- [ ] `DATABASE_URL` set via Render Dashboard (not in `render.yaml`) — marked `sync: false`
- [ ] Supabase connection uses `?sslmode=require`
- [ ] `/metrics` endpoint — consider IP-restrict or basic auth in production
- [ ] WebSocket `/v1/fleet/live` currently has **no JWT guard** — flagged issue, add `authenticate()` wrapper
- [ ] `APP_ENV=production` set — application can use this to enable stricter runtime checks
- [ ] Rate limiting is active on all public endpoints (`RateLimiting.kt`)
- [ ] Idempotency keys enforced on payment endpoints
- [ ] All secrets are rotated if accidentally exposed via `git log`
- [ ] Render plan: upgrade from `free` to `starter` for production traffic (no spin-down)

---

## Quick Reference

### Start Dev in 3 Commands
```powershell
docker-compose up -d          # Start PostgreSQL + Redis
.\gradlew.bat run             # Start API server
Start-Process "http://127.0.0.1:8080/swagger"   # Open Swagger UI
```

### Deploy to Production
```powershell
git add .
git commit -m "feat: your change"
git push origin main
# CI runs → Render auto-deploys → wait ~3 minutes
# Check: https://fleet-management-api-8tli.onrender.com/health
```

---

*Last Updated: 2026-03-08*
