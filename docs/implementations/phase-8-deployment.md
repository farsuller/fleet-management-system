# Phase 8 — Deployment

## Status

- Overall: **~40% Complete** (Partially Ready)
- Compliance Date: 2026-02-15
- Implementation Date: In Progress
- Verification: Pending
- **Deployment Ready:** ❌ NO (6 critical blockers)

---

## Purpose

Design and implement the production deployment strategy for the Fleet Management system.

---

## Depends on

- Phase 1 baseline (health endpoints, config strategy)
- Phases 2–7 as applicable (services exist to deploy)

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
| **Database Migrations** | ✅ Complete | Flyway auto-runs on startup via `Databases.kt` |
| **Health Endpoint** | ✅ Complete | `/health` implemented in `Routing.kt` |
| **Docker Compose (Postgres/Redis)** | ✅ Complete | Local dev environment ready |
| **Connection Pooling** | ✅ Complete | HikariCP configured |
| **Structured Logging** | ✅ Complete | JSON logs (Phase 4) |
| **Dockerfile** | ❌ Missing | **BLOCKER** - Container image required |
| **render.yaml** | ❌ Missing | **BLOCKER** - Platform config required |
| **Environment Variables** | ❌ Missing | **BLOCKER** - Hardcoded config (port, DB, Redis, JWT) |
| **Fat JAR Build** | ❌ Missing | **BLOCKER** - `buildFatJar` task needed |
| **Docker Compose (App)** | ⚠️ Incomplete | Missing app service definition |
| **CI/CD Pipeline** | Not Started | GitHub Actions for auto-deploy |
| **Backup Procedures** | Not Started | Documentation needed |
| **Kubernetes/EKS** | Deferred | Future reference only |

---

## 🚨 Critical Actions Required

**Before deploying to Render, the following 6 items MUST be implemented:**

### 1. Create Dockerfile (Priority: 🔴 Critical)

**File:** `Dockerfile` (root directory)

```dockerfile
# Multi-stage build for minimal runtime image
FROM gradle:8-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle buildFatJar --no-daemon

# Runtime stage with minimal JRE
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar

# Health check for Render
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/health || exit 1

# Expose port (Render will inject PORT env var)
EXPOSE ${PORT:-8080}

# Run application
CMD ["java", "-jar", "app.jar"]
```

**Also create:** `.dockerignore`
```
.git
.gradle
build
*.md
.env
.idea
*.iml
```

---

### 2. Create render.yaml (Priority: 🔴 Critical)

**File:** `render.yaml` (root directory)

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
      - key: ENVIRONMENT
        value: production
    healthCheckPath: /health
    autoDeploy: true

databases:
  - name: fleet-management-db
    databaseName: fleet_management
    plan: starter

  - name: fleet-management-redis
    plan: starter
```

---

### 3. Update application.yaml (Priority: 🔴 Critical)

**File:** `src/main/resources/application.yaml`

**Current (Hardcoded):**
```yaml
ktor:
  deployment:
    port: 8080  # ❌ HARDCODED

storage:
  jdbcUrl: "jdbc:postgresql://127.0.0.1:5435/fleet_db"  # ❌ HARDCODED
  username: "fleet_user"  # ❌ HARDCODED
  password: "secret_123"  # ❌ HARDCODED

jwt:
  secret: "change-me-in-production-use-env-var-min-64-chars"  # ❌ HARDCODED
```

**Required (Environment Variables):**
```yaml
ktor:
  deployment:
    port: ${PORT:8080}
    host: 0.0.0.0  # Required for Render

storage:
  jdbcUrl: ${DATABASE_URL}
  username: ${DATABASE_USER:}
  password: ${DATABASE_PASSWORD:}
  driverClassName: org.postgresql.Driver
  maximumPoolSize: ${DB_POOL_SIZE:10}

redis:
  url: ${REDIS_URL:redis://localhost:6379}

jwt:
  secret: ${JWT_SECRET}
  issuer: ${JWT_ISSUER:fleet-management}
  audience: ${JWT_AUDIENCE:fleet-users}
  realm: "Fleet Management Access"
  expiresIn: ${JWT_EXPIRES_IN:3600000}
```

---

### 4. Update Application.kt (Priority: 🔴 Critical)

**File:** `src/main/kotlin/com/solodev/fleet/Application.kt`

**Current (Line 37):**
```kotlin
// ❌ HARDCODED REDIS
val jedis = Jedis("localhost", 6379)
```

**Required:**
```kotlin
// Read Redis URL from config
val redisUrl = environment.config.propertyOrNull("redis.url")?.getString()
    ?: "redis://localhost:6379"

// Parse Redis URL (format: redis://host:port)
val redisUri = java.net.URI(redisUrl)
val redisHost = redisUri.host ?: "localhost"
val redisPort = if (redisUri.port > 0) redisUri.port else 6379

val jedis = Jedis(redisHost, redisPort)
```

---

### 5. Add Fat JAR Build Task (Priority: 🔴 Critical)

**File:** `build.gradle.kts`

**Add after the `application` block:**
```kotlin
ktor {
    fatJar {
        archiveFileName.set("fleet-management-all.jar")
    }
}
```

**Verify:**
```bash
./gradlew buildFatJar
# Should produce: build/libs/fleet-management-all.jar
```

---

### 6. Complete docker-compose.yml (Priority: 🟡 Medium)

**File:** `docker-compose.yml`

**Add the `app` service:**
```yaml
services:
  postgres:
    # ... existing config ...

  redis:
    # ... existing config ...

  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      PORT: 8080
      DATABASE_URL: jdbc:postgresql://postgres:5432/fleet_db
      DATABASE_USER: fleet_user
      DATABASE_PASSWORD: secret_123
      REDIS_URL: redis://redis:6379
      JWT_SECRET: local-dev-secret-change-in-production
      JWT_ISSUER: fleet-management-local
      JWT_AUDIENCE: fleet-users-local
      ENVIRONMENT: development
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8080/health"]
      interval: 10s
      timeout: 3s
      retries: 5
```

---

## ✅ Verification Steps

After implementing the above changes:

1. **Test Fat JAR Build:**
   ```bash
   ./gradlew buildFatJar
   ls -lh build/libs/*-all.jar
   ```

2. **Test Docker Build:**
   ```bash
   docker build -t fleet-management:test .
   ```

3. **Test Full Stack Locally:**
   ```bash
   docker-compose up -d
   docker-compose logs -f app
   curl http://localhost:8080/health
   ```

4. **Test Environment Variables:**
   ```bash
   export PORT=9000
   export DATABASE_URL=jdbc:postgresql://localhost:5435/fleet_db
   export REDIS_URL=redis://localhost:6379
   ./gradlew run
   ```

---

## Definition of Done (Phase 8)

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
├── Dockerfile                              (Phase 8)
├── docker-compose.yml                      (Phase 8)
├── render.yaml                             (Phase 8)
├── .dockerignore                           (Phase 8)
├── .github/
│   └── workflows/
│       ├── ci.yml                          (Phase 8)
│       └── deploy.yml                      (Phase 8)
├── scripts/
│   ├── deploy.sh                           (Phase 8)
│   ├── rollback.sh                         (Phase 8)
│   └── backup.sh                           (Phase 8)
└── docs/
    └── deployment/
        ├── render.md                       (Phase 8)
        ├── local-dev.md                    ✅ (Phase 1)
        ├── environment-variables.md        ✅ (Phase 1)
        ├── backup-recovery.md              (Phase 8)
        └── eks-checklist.md                (Phase 9 - Future)
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
| Health endpoints | ✅ Complete | `/health` in `Routing.kt:84` |
| Configuration strategy | ⚠️ Partial | Exists but hardcoded |
| Observability baseline | ✅ Complete | JSON logging (Phase 4) |

### Phase 2-7 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| All features implemented | ✅ Complete | 20 tables, 7 modules ready |
| Flyway migrations | ✅ Complete | 12 migrations (V001-V012) |

### Phase 8 Requirements
| Requirement | Status | Priority | Notes |
|-------------|--------|----------|-------|
| **Dockerfile** | ❌ Missing | 🔴 Critical | Container image required |
| **render.yaml** | ❌ Missing | 🔴 Critical | Platform config required |
| **Environment Variables** | ❌ Missing | 🔴 Critical | Port, DB, Redis, JWT hardcoded |
| **Fat JAR Build** | ❌ Missing | 🔴 Critical | `buildFatJar` task needed |
| Docker Compose (Full) | ⚠️ Partial | 🟡 Medium | Missing app service |
| Database provisioning | ⏳ Ready | 🟢 Low | Flyway auto-runs |
| Redis provisioning | ⏳ Ready | 🟢 Low | Compatible |
| Health checks | ✅ Complete | 🟢 Low | Already implemented |
| Observability | ✅ Complete | 🟢 Low | JSON logs ready |
| CI/CD | ❌ Missing | 🟡 Medium | GitHub Actions needed |
| Backup procedures | ❌ Missing | 🟡 Medium | Documentation needed |

**Overall Compliance**: **~40%** (Partially Ready)

**Deployment Blockers:** 4 critical items (estimated 2 hours to resolve)

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

**Phase 8 Status**: **Not Started**

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
