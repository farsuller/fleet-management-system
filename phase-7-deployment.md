# Phase 7 — Deployment (Render)

## Status

- Overall: **Not Started**
- Implementation Date: TBD
- Verification: Pending

---

## Purpose

Make the system runnable locally and deployable to **Render** with safe configuration, health checks, and observability. We prioritize Render for MVP speed and simplicity, while keeping EKS notes for future scaling.

---

## Depends on

- Phase 1 baseline (health endpoints, config strategy)
- Phases 2–6 as applicable (services exist to deploy)

---

## Inputs / Constraints

- **Platform**: Render (managed platform)
- **Architecture**: Single web service (modular monolith)
- **Database**: Render Postgres (managed)
- **Cache**: Render Redis (managed)
- **Messaging**: Kafka deferred for MVP
- **Container**: Docker-based deployment
- **CI/CD**: GitHub integration with auto-deploy
- **Observability**: Structured logs to stdout
- **Scaling**: Single instance initially

---

## Implementation Breakdown

| Item | Status | Notes / Definition |
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

---

## Definition of Done (Phase 7)

- [ ] Local environment can run the system reliably (`docker-compose up`)
- [ ] **Render deployment is fully operational**:
  - [ ] Web Service connected to GitHub & auto-deploys
  - [ ] Env vars (`DATABASE_URL`, `REDIS_URL`, `JWT_SECRET`, `PORT`) configured safely
  - [ ] Postgres + Redis provisioned and connected
  - [ ] Health checks (`/health`) passing
  - [ ] Background jobs running safely (single instance)
- [ ] Production configuration is externalized (no hardcoded secrets)
- [ ] Migrations run automatically on deployment
- [ ] Monitoring and logging operational
- [ ] Backup and recovery procedures documented
- [ ] EKS/Kubernetes manifests are optional/deferred

---

## Implementation Summary

### ✅ Core Features Implemented

*This section will be populated during implementation with:*

#### 1. **Dockerfile**
```dockerfile
# Build stage
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle buildFatJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/health || exit 1

# Run application
EXPOSE ${PORT:-8080}
CMD ["java", "-jar", "app.jar"]
```

#### 2. **Docker Compose (Local Dev)**
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: fleet_management
      POSTGRES_USER: fleet_user
      POSTGRES_PASSWORD: fleet_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U fleet_user"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/fleet_management
      DATABASE_USER: fleet_user
      DATABASE_PASSWORD: fleet_pass
      REDIS_URL: redis://redis:6379
      JWT_SECRET: local-dev-secret-change-in-production
      PORT: 8080
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

volumes:
  postgres_data:
```

#### 3. **Render Configuration**
**render.yaml**:
```yaml
services:
  - type: web
    name: fleet-management-api
    env: docker
    dockerfilePath: ./Dockerfile
    envVars:
      - key: PORT
        value: 10000
      - key: DATABASE_URL
        fromDatabase:
          name: fleet-management-db
          property: connectionString
      - key: REDIS_URL
        fromService:
          name: fleet-management-redis
          type: redis
          property: connectionString
      - key: JWT_SECRET
        generateValue: true
      - key: JWT_ISSUER
        value: fleet-management-api
      - key: JWT_AUDIENCE
        value: fleet-management-users
    healthCheckPath: /health
    autoDeploy: true

databases:
  - name: fleet-management-db
    databaseName: fleet_management
    plan: starter

  - name: fleet-management-redis
    plan: starter
```

#### 4. **Application Configuration**
**application.yaml** (with environment variable support):
```yaml
ktor:
  deployment:
    port: ${PORT:8080}
    host: 0.0.0.0
  application:
    modules:
      - com.example.ApplicationKt.module

database:
  url: ${DATABASE_URL}
  user: ${DATABASE_USER:}
  password: ${DATABASE_PASSWORD:}
  driver: org.postgresql.Driver
  maxPoolSize: ${DB_POOL_SIZE:10}

redis:
  url: ${REDIS_URL}
  maxConnections: ${REDIS_MAX_CONNECTIONS:10}

jwt:
  secret: ${JWT_SECRET}
  issuer: ${JWT_ISSUER:fleet-management}
  audience: ${JWT_AUDIENCE:fleet-users}
  realm: ${JWT_REALM:fleet-management}
  expirationSeconds: ${JWT_EXPIRATION:3600}
```

#### 5. **Startup Migration**
```kotlin
// Run migrations on startup
fun Application.configureDatabases() {
    val flyway = Flyway.configure()
        .dataSource(
            environment.config.property("database.url").getString(),
            environment.config.property("database.user").getString(),
            environment.config.property("database.password").getString()
        )
        .load()
    
    // Run migrations
    flyway.migrate()
    
    // Configure database connection
    Database.connect(/* ... */)
}
```

---

## Verification

### Deployment Tests

*This section will be populated with:*
- Local deployment verification
- Render deployment verification
- Health check validation
- Migration execution confirmation
- Performance under load
- Backup and recovery tests

---

## Architecture Structure

### Deployment Artifacts
```
.
├── Dockerfile                              (Phase 7)
├── docker-compose.yml                      (Phase 7)
├── render.yaml                             (Phase 7)
├── .dockerignore                           (Phase 7)
├── .github/
│   └── workflows/
│       ├── ci.yml                          (Phase 7)
│       └── deploy.yml                      (Phase 7)
├── scripts/
│   ├── deploy.sh                           (Phase 7)
│   ├── rollback.sh                         (Phase 7)
│   └── backup.sh                           (Phase 7)
└── docs/
    └── deployment/
        ├── render.md                       (Phase 7)
        ├── local-dev.md                    ✅ (Phase 1)
        ├── environment-variables.md        ✅ (Phase 1)
        ├── backup-recovery.md              (Phase 7)
        └── eks-checklist.md                (Phase 7 - Future)
```

---

## Code Impact (Repository Artifacts)

### Files Created (Expected)

**Deployment Configuration** (~6 files):
1. `Dockerfile` - Container image definition
2. `docker-compose.yml` - Local development environment
3. `render.yaml` - Render platform configuration
4. `.dockerignore` - Exclude unnecessary files from image
5. `scripts/deploy.sh` - Deployment automation
6. `scripts/rollback.sh` - Rollback procedures

**CI/CD** (~2 files):
1. `.github/workflows/ci.yml` - Continuous integration
2. `.github/workflows/deploy.yml` - Continuous deployment

**Scripts** (~3 files):
1. `scripts/backup.sh` - Database backup
2. `scripts/restore.sh` - Database restore
3. `scripts/health-check.sh` - Health verification

### Files Modified
1. `src/main/kotlin/Application.kt` - Add startup migrations
2. `src/main/resources/application.yaml` - Environment variable support
3. `build.gradle.kts` - Fat JAR configuration
4. `.gitignore` - Ignore deployment artifacts

### Configuration Files
- `render.yaml` - Render platform configuration
- `docker-compose.yml` - Local development
- `.env.example` - Environment variable template

### Documentation
- `docs/deployment/render.md` - Render deployment guide
- `docs/deployment/backup-recovery.md` - Backup procedures
- `docs/deployment/troubleshooting.md` - Common issues
- `docs/deployment/eks-checklist.md` - Future Kubernetes migration

---

## Key Achievements

*This section will be populated during implementation with:*
1. **One-Click Deployment** - Auto-deploy from GitHub
2. **Zero-Downtime Migrations** - Safe database updates
3. **Health Monitoring** - Automatic health checks
4. **Scalable Foundation** - Ready for horizontal scaling
5. **Disaster Recovery** - Backup and restore procedures

---

## Compliance Status

### Phase 1 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Health endpoints | ✅ | `/health` implemented |
| Configuration strategy | ✅ | Environment variables |
| Observability baseline | ✅ | Structured logging |

### Phase 2-6 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| All features implemented | ✅ | Ready to deploy |

### Phase 7 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Dockerfile | Not Started | Container image |
| Docker Compose | Not Started | Local development |
| Render configuration | Not Started | Platform setup |
| Environment variables | Not Started | Externalized config |
| Database provisioning | Not Started | Managed Postgres |
| Redis provisioning | Not Started | Managed Redis |
| Health checks | Not Started | Wired to platform |
| Background jobs | Not Started | Single instance |
| Observability | Not Started | Structured logs |
| CI/CD | Not Started | Auto-deploy |
| Backup procedures | Not Started | Documentation |

**Overall Compliance**: **0%** (Not Started)

---

## How to Run

### Local Development
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop services
docker-compose down

# Rebuild after code changes
docker-compose up -d --build app
```

### Build Docker Image
```bash
# Build image
docker build -t fleet-management:latest .

# Run container
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://localhost:5432/fleet_management \
  -e REDIS_URL=redis://localhost:6379 \
  -e JWT_SECRET=your-secret \
  fleet-management:latest
```

### Deploy to Render
```bash
# 1. Push to GitHub
git push origin main

# 2. Render auto-deploys (if configured)
# Or manually trigger via Render dashboard

# 3. Check deployment status
curl https://fleet-management-api.onrender.com/health
```

### Run Migrations Manually
```bash
# Connect to Render shell
render shell fleet-management-api

# Run migrations
./gradlew flywayMigrate
```

### Backup Database
```bash
# From Render dashboard or CLI
render pg:backup fleet-management-db

# Download backup
render pg:backups:download fleet-management-db
```

### Expected Behavior
- Application starts and binds to `0.0.0.0:$PORT`
- Migrations run automatically on startup
- Health check returns 200 OK
- Logs are structured JSON to stdout
- Auto-deploy triggers on GitHub push
- Zero downtime during deployments

---

## Next Steps

### Immediate
- [ ] Create Dockerfile for container image
- [ ] Set up Docker Compose for local development
- [ ] Create Render account and configure services
- [ ] Provision Postgres and Redis on Render
- [ ] Configure environment variables
- [ ] Set up GitHub integration for auto-deploy
- [ ] Configure health checks
- [ ] Test deployment end-to-end
- [ ] Document backup and recovery procedures
- [ ] Set up monitoring and alerting

### Post-Deployment
- [ ] Monitor application performance
- [ ] Set up automated backups
- [ ] Configure alerting for errors
- [ ] Implement log aggregation
- [ ] Plan for horizontal scaling
- [ ] Consider CDN for static assets
- [ ] Evaluate migration to Kubernetes (if needed)

### Future Enhancements
- **Kubernetes/EKS**: For advanced scaling and orchestration
- **Multi-region**: Geographic distribution
- **Blue-green deployments**: Zero-downtime updates
- **Canary releases**: Gradual rollouts
- **Auto-scaling**: Based on metrics

---

## Deployment Guide (Render)

### 1. Target Architecture
- **One Web Service**: Kotlin + Ktor (modular monolith)
- **Managed Postgres**: Primary datastore
- **Managed Redis**: Cache & ephemeral state
- **No Kafka**: Intentionally omitted for MVP

### 2. Build Strategy
**Recommended**: Docker-based build
- Use a `Dockerfile` to produce a minimal runtime image (JDK 21 slim)
- Avoid relying on Render native builds for JVM services (often slower/custom)

### 3. Runtime Configuration
- Ktor must bind to `0.0.0.0`
- Port must use `PORT` env var (Render injects this, usually `10000`)

### 4. Database & Migrations
- Use **Render Postgres** (internal connection string)
- Run Flyway migrations at **application startup** (safest for single-instance MVP)
- *Warning*: Avoid concurrent migrations if you scale to >1 instance

### 5. Background Jobs
- Allowed only under **single-instance** deployment
- Use coroutine-based schedulers or Quartz (non-clustered)
- *Warning*: Do not enable multiple instances yet

### 6. Observability
- Emit structured (JSON) logs to stdout
- Use Render logs dashboard
- Optional: Export traces/metrics via OpenTelemetry if needed later

---

## References

### Project Documentation
- `fleet-management-plan.md` - Overall project plan
- `phase-6-hardening.md` - Previous phase

### Skills Documentation
- `skills/backend-development/SKILL.md` - Backend principles
- `skills/deployment/SKILL.md` - Deployment best practices (if exists)

### Deployment Documentation
- `docs/deployment/render.md` - Render guide (to be created)
- `docs/deployment/local-dev.md` - Local development (exists)
- `docs/deployment/environment-variables.md` - Configuration (exists)
- `docs/deployment/backup-recovery.md` - Backup procedures (to be created)
- `docs/deployment/eks-checklist.md` - Future Kubernetes (to be created)
- `render_deployment_guide.md` - Render reference

---

## Summary

**Phase 7 Status**: **Not Started**

This phase will deploy the fleet management system to Render with managed Postgres and Redis. The deployment will be automated via GitHub integration with health checks and structured logging.

**Key Deliverables**:
- [ ] Dockerfile for containerization
- [ ] Docker Compose for local development
- [ ] Render configuration (render.yaml)
- [ ] Environment variable configuration
- [ ] Automated migrations on startup
- [ ] Health check integration
- [ ] CI/CD pipeline (GitHub → Render)
- [ ] Backup and recovery procedures
- [ ] Monitoring and logging
- [ ] Deployment documentation

**Production Ready**: Not Yet

Once Phase 7 is complete, the system will be live on Render and accessible to users.

---

**Implementation Date**: TBD  
**Verification**: Pending  
**Deployment Status**: Not Started  
**Compliance**: 0%  
**Production Ready**: Not Yet
