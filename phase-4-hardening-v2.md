# Phase 4 — Hardening v2

## Status

- Overall: **IN PROGRESS**
- Implementation Date: 2026-02-14
- Verification: In Progress

---

## Purpose

Improve correctness, reliability, and performance under real-world concurrency and load.

---

## Depends on

- Phases 1–4 (baseline functionality exists)
- Phase 3 Hardening Implementation (initial security baseline)

---

## Inputs / Constraints

- Production-level reliability requirements
- Performance SLAs (response time, throughput)
- Security best practices (OWASP API Top 10)
- Observability standards (golden signals)
- Concurrency correctness guarantees
- Failure mode resilience

---

## Implementation Breakdown

| Item | Status | Notes / Definition |
|------|--------|-------------------|
| Transaction boundaries review | Not Started | Ensure strong consistency for rentals/accounting; avoid partial writes |
| Concurrency strategy | Not Started | Locking/advisory locks where needed; avoid race conditions |
| Double-booking tests | Not Started | Prove DB exclusion constraint works under load |
| Kafka backpressure + consumer tuning | Not Started | Lag monitoring, retries, DLQ volume control |
| Redis usage audit | Not Started | TTLs defined; no source-of-truth usage; cache stampede mitigation |
| Rate limiting | ✅ Completed | Applied early in Phase 3 (Hardening) |
| Security hardening | Not Started | Auth/authz tests; least privilege; OWASP API Top 10 checklist |
| Observability completeness | Not Started | Golden signals metrics; tracing coverage; structured log fields |
| Performance tuning | Not Started | Index tuning, query plan improvements, N+1 elimination |
| Failure-mode testing | Not Started | Dependency outages, timeouts, partial failures, replay safety |
| Structured JSON logging | Not Started | Consistent log format with correlation IDs |
| Circuit breakers | Not Started | Prevent cascading failures |

---

## Definition of Done (Phase 6)

- [ ] Concurrency correctness is demonstrated (especially rental overlap prevention)
- [ ] Kafka consumers handle retries/DLQs safely under load
- [ ] Rate limiting, security checks, and observability are production-ready
- [ ] Load tests pass with acceptable performance
- [ ] Security audit completed (OWASP API Top 10)
- [ ] Structured logging implemented across all services
- [ ] Circuit breakers protect against cascading failures
- [ ] Failure mode tests validate resilience
- [ ] Performance benchmarks meet SLAs

---

## Implementation Summary

### ✅ Core Features Implemented

*This section will be populated during implementation with:*

#### 1. **Concurrency Control (Optimistic & Pessimistic)**
*Purpose: Prevent race conditions and ensure data integrity during simultaneous updates (e.g., double-booking).*

**Dependency:**
Built-in to **Exposed ORM** and **PostgreSQL**. No additional libraries required.

**Code Implementation:**
We use both **Optimistic Locking** (for high-concurrency/low-conflict scenarios) and **Pessimistic Locking** (for critical sections like reservations).

```kotlin
// 1. Optimistic (Version-based)
data class Vehicle(val id: UUID, val version: Long, ...)

// 2. Pessimistic (Advisory Locks)
fun reserveVehicle(vehicleId: UUID, rentalPeriod: ClosedRange<Instant>) {
    transaction {
        exec("SELECT pg_advisory_xact_lock(?)", vehicleId.hashCode())
        // ... safety checks ...
    }
}
```

**Applying Method:**
Apply locking directly in the **Service** or **Use Case** layer where transaction boundaries are defined. 

- Use **Optimistic** for simple updates (e.g., updating vehicle mileage).
- Use **Pessimistic (Advisory Locks)** for complex coordination (e.g., preventing two users from reserving the same vehicle at the exact same millisecond).

### ✅ Current Implementation (Hardened Components)

This section documents the hardening features already integrated into the Fleet Management codebase.

#### 1. **SQL Injection Protection**
*Purpose: Ensure all database interactions are type-safe and resistant to query manipulation.*

**Dependency:**
```kotlin
// build.gradle.kts
implementation(libs.exposed.core) // Type-safe SQL DSL
```

**Code Implementation:**
We use Exposed's **Type-Safe DSL** which translates Kotlin code into parameterized SQL queries at runtime.

```kotlin
// VehicleRepositoryImpl.kt
override suspend fun findById(id: VehicleId): Vehicle? = dbQuery {
    VehiclesTable.selectAll()
        .where { VehiclesTable.id eq UUID.fromString(id.value) }
        .singleOrNull()?.toVehicle()
}
```

**Applying Method:**
All new modules must use the `Repository` pattern. No raw string interpolation is allowed for query building.

**Key Advantages:**
- **Parameterized by Default**: Values are passed as parameters, not as part of the query string.
- **Compile-time Safety**: Typo in a column name results in a build error, not a runtime vulnerability.

---

#### 2. **API Rate Limiting (Ktor Native)**
*Purpose: Protect endpoints from brute-force attacks and resource exhaustion.*

**Dependency:**
```kotlin
// build.gradle.kts
implementation(libs.ktor.server.rate.limit)
```

**Code Implementation:**
A multi-tiered configuration in `RateLimiting.kt` that scales limits based on user trust levels.

```kotlin
// RateLimiting.kt
install(RateLimit) {
    register(RateLimitName("auth_strict")) {
        rateLimiter(limit = 5, refillPeriod = 1.minutes)
        requestKey { call -> call.request.origin.remoteHost }
    }
}
```

**Applying Method:**
Apply the `rateLimit` block surrounding route groups in `Routing.kt`.

**Key Advantages:**
- **Brute-Force Shield**: Prevents attackers from attempting thousands of logins.
- **Fair Usage**: Ensures no single user can overload the public API.

---

#### 3. **Netty Asynchronous Engine**
*Purpose: High-performance, non-blocking asynchronous server core.*

**Dependency:**
```kotlin
// build.gradle.kts
implementation(libs.ktor.server.netty)
```

**Code Implementation:**
The application entry point is configured to boot via Netty's `EngineMain`.

```kotlin
// Application.kt
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}
```

**Key Advantages:**
- **Concurrency**: Specifically optimized for handling thousands of concurrent connections using Kotlin Coroutines.
- **Resource Efficiency**: Low memory footprint compared to traditional servlet containers.

---

#### 4. **Idempotency (Safety Retries)**
*Purpose: Prevent duplicate side-effects (e.g. double-billing) during network retries.*

**Dependency:**
Built-in to **Infrastructure Layer** using SQL unique constraints.

**Code Implementation:**
The `Idempotency` plugin checks for a unique header and stores the result of the first successful request.

```kotlin
// Idempotency.kt
install(Idempotency) {
    headerName = "X-Idempotency-Key"
    repository = IdempotencyRepositoryImpl()
}
```

**Applying Method:**
Clients must send a unique UUID in the `X-Idempotency-Key` header for POST/PUT operations.

**Key Advantages:**
- **Double-Post Prevention**: Safe to retry if the client times out but the server succeeded.
- **Data Integrity**: Critical for accounting and payment processing.

---

#### 5. **Cursor-Based Pagination**
*Purpose: Efficient, stable listing of large datasets.*

**Code Implementation:**
Using Stable Sort on unique keys (UUIDs) to prevent items from being skipped or duplicated during scrolling.

```kotlin
// VehicleRepositoryImpl.kt
override suspend fun findAll(params: PaginationParams) = dbQuery {
    var query = VehiclesTable.selectAll()
    params.cursor?.let { lastId ->
        query = query.where { VehiclesTable.id greater UUID.fromString(lastId) }
    }
    query.orderBy(VehiclesTable.id to SortOrder.ASC).limit(params.limit)
}
```

**Key Advantages:**
- **Constant Performance**: `O(1)` jump to the next page, unlike `LIMIT/OFFSET`.
- **Scrolling Stability**: No duplicates if new vehicles are added while the user is listing.

---

### 🚀 Planned Hardening (Professional Upgrades)

These components are targeted for full implementation as the system scales.

#### 6. **Distributed Rate Limiting (Bucket4j + Redis)**
*Status: Planned for Multi-Instance Support*

**Dependency:**
```kotlin
// build.gradle.kts
implementation("com.github.vladimir-bukhtoyarov:bucket4j-redis:7.6.0")
```

**Code Implementation:**
Moving from in-memory (Local) to Redis-backed (Global) rate limiting to support horizontal scaling.

```kotlin
// DistributedRateLimiter.kt
fun Application.configureDistributedRateLimiting(jedis: Jedis) {
    val proxyManager = RedisJedisProxyManager(jedis)
    val configuration = BucketConfiguration.builder()
        .addLimit(Bandwidth.simple(5, Duration.ofMinutes(1)))
        .build()
        
    // Usage in Route
    val bucket = proxyManager.builder().build(userId.toByteArray(), configuration)
}
```

**Applying Method:**
Nesting the Bucket4j check inside the Ktor `rateLimit` (Moat) layer for a Defense in Depth strategy.

---

#### 7. **Distributed Caching (Redis)**
*Status: Planned for DB Relief*

**Dependency:**
```kotlin
// build.gradle.kts
implementation("redis.clients:jedis:5.1.0")
```

**Code Implementation:**
Creating a generic `CacheManager` to handle JSON serialization and TTLs.

```kotlin
// RedisCacheManager.kt
class RedisCacheManager(private val jedis: Jedis) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun <T> getOrSet(key: String, ttlSeconds: Long, fetcher: suspend () -> T?): T? {
        val cached = jedis.get(key)
        if (cached != null) return json.decodeFromString(cached)

        val data = fetcher()
        if (data != null) {
            jedis.setex(key, ttlSeconds, json.encodeToString(data))
        }
        return data
    }
}
```

**Applying Method:**
Apply caching in the **Repositories** using the **Cache-Aside** pattern to keep Domain logic clean.

---

#### 8. **Circuit Breakers**
*Status: Planned for External Resilience*

**Code Implementation:**
Wrap external calls in circuit breakers to prevent cascading failures.

```kotlin
// CircuitBreaker.kt
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeout: Duration = 60.seconds
) {
    suspend fun <T> execute(block: suspend () -> T): T {
        if (state == State.OPEN) throw CircuitBreakerOpenException()
        return try {
            val result = block()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }
}
```

**Applying Method:**
Used specifically for external integrations (e.g., Payment Gateways, Third-party APIs).

#### 9. **Structured JSON Logging**
*Status: Planned for Log Correlation*

**Logic:**
Implement Logback's `LogstashEncoder` to output logs in machine-readable JSON format for ELK/Datadog.

---

## Verification & Tracking

### Compliance Status
| Requirement | Status | Notes |
|-------------|--------|-------|
| SQLi Protection | ✅ Completed | Using Exposed Type-Safe DSL |
| Netty Engine | ✅ Completed | Configured in Application.kt |
| Local Rate Limiting | ✅ Completed | Tiered Ktor Native Plugin |
| Idempotency | ✅ Completed | Header-based key tracking |
| Pagination | ✅ Completed | Cursor-based strategy |
| Redis Caching | ⏳ Planned | Phase 6 Final |
| Global Rate Limiting | ⏳ Planned | Bucket4j upgrade |
| Circuit Breakers | ⏳ Planned | Reliability upgrade |
| JSON Logging | ⏳ Planned | Observability upgrade |

---

## Architecture Structure

### Hardening Components
```
src/main/kotlin/com/example/
└── shared/
    ├── infrastructure/
    │   ├── ratelimiting/
    │   │   ├── RateLimiter.kt             (Phase 6)
    │   │   └── RateLimitingPlugin.kt      (Phase 6)
    │   ├── circuitbreaker/
    │   │   ├── CircuitBreaker.kt          (Phase 6)
    │   │   └── CircuitBreakerConfig.kt    (Phase 6)
    │   ├── caching/
    │   │   ├── CacheManager.kt            (Phase 6)
    │   │   └── CacheStrategy.kt           (Phase 6)
    │   └── logging/
    │       ├── StructuredLogger.kt        (Phase 6)
    │       └── LoggingPlugin.kt           (Phase 6)
    └── monitoring/
        ├── metrics/
        │   ├── MetricsCollector.kt        (Phase 6)
        │   └── GoldenSignals.kt           (Phase 6)
        └── health/
            └── HealthChecks.kt             (Phase 6)

src/test/kotlin/com/example/
├── concurrency/
│   ├── DoubleBookingTest.kt               (Phase 6)
│   ├── ConcurrentUpdateTest.kt            (Phase 6)
│   └── RaceConditionTest.kt               (Phase 6)
├── performance/
│   ├── LoadTest.kt                        (Phase 6)
│   └── StressTest.kt                      (Phase 6)
└── security/
    ├── AuthenticationTest.kt              (Phase 6)
    ├── AuthorizationTest.kt               (Phase 6)
    └── OwaspTop10Test.kt                  (Phase 6)

docs/runbooks/
├── incident-response.md                   (Phase 6)
├── performance-tuning.md                  (Phase 6)
├── circuit-breaker-recovery.md            (Phase 6)
└── kafka-lag-mitigation.md                (Phase 6)
```

---

## Code Impact (Repository Artifacts)

### Files Created (Expected)

**Rate Limiting** (~3 files):
1. `src/main/kotlin/com/example/shared/infrastructure/ratelimiting/RateLimiter.kt`
2. `src/main/kotlin/com/example/shared/infrastructure/ratelimiting/RateLimitingPlugin.kt`
3. `src/main/kotlin/com/example/shared/infrastructure/ratelimiting/RateLimitConfig.kt`

**Circuit Breakers** (~3 files):
1. `src/main/kotlin/com/example/shared/infrastructure/circuitbreaker/CircuitBreaker.kt`
2. `src/main/kotlin/com/example/shared/infrastructure/circuitbreaker/CircuitBreakerConfig.kt`
3. `src/main/kotlin/com/example/shared/infrastructure/circuitbreaker/CircuitBreakerPlugin.kt`

**Structured Logging** (~2 files):
1. `src/main/kotlin/com/example/shared/infrastructure/logging/StructuredLogger.kt`
2. `src/main/kotlin/com/example/shared/infrastructure/logging/LoggingPlugin.kt`

**Caching** (~3 files):
1. `src/main/kotlin/com/example/shared/infrastructure/caching/CacheManager.kt`
2. `src/main/kotlin/com/example/shared/infrastructure/caching/CacheStrategy.kt`
3. `src/main/kotlin/com/example/shared/infrastructure/caching/RedisCache.kt`

**Monitoring** (~4 files):
1. `src/main/kotlin/com/example/shared/monitoring/metrics/MetricsCollector.kt`
2. `src/main/kotlin/com/example/shared/monitoring/metrics/GoldenSignals.kt`
3. `src/main/kotlin/com/example/shared/monitoring/health/HealthChecks.kt`
4. `src/main/kotlin/com/example/shared/monitoring/health/DependencyHealthCheck.kt`

**Tests** (~10+ files):
- Concurrency tests
- Load tests
- Security tests
- Failure mode tests

**Runbooks** (~5+ files):
- Incident response procedures
- Performance tuning guides
- Recovery procedures

### Files Modified
1. `src/main/kotlin/Application.kt` - Add hardening plugins
2. All route files - Add rate limiting
3. Repository implementations - Add caching
4. External service clients - Add circuit breakers
5. `build.gradle.kts` - Add monitoring dependencies

### Configuration Files
- `src/main/resources/application.yaml` - Rate limits, circuit breaker thresholds
- `src/main/resources/logback.xml` - Structured logging configuration
- `docker-compose.yml` - Monitoring tools (Prometheus, Grafana)

### Documentation
- `docs/runbooks/incident-response.md` - Incident procedures
- `docs/runbooks/performance-tuning.md` - Performance optimization
- `docs/runbooks/circuit-breaker-recovery.md` - Circuit breaker recovery
- `docs/runbooks/kafka-lag-mitigation.md` - Kafka lag handling
- `docs/security/owasp-checklist.md` - Security audit checklist

---

## Key Achievements

*This section will be populated during implementation with:*
1. **Concurrency Correctness** - No race conditions or double-bookings
2. **Resilience** - Circuit breakers prevent cascading failures
3. **Performance** - Meets SLAs under load
4. **Security** - OWASP API Top 10 compliance
5. **Observability** - Complete visibility into system behavior

---

## How to Run

### Load Testing
```bash
# Install k6 for load testing
choco install k6

# Run load test
k6 run tests/load/rental-reservation-load-test.js
```

### Concurrency Testing
```bash
# Run concurrency tests
./gradlew test --tests "*ConcurrencyTest"
```

### Security Audit
```bash
# Run OWASP dependency check
./gradlew dependencyCheckAnalyze

# Run security tests
./gradlew test --tests "*SecurityTest"
```

### Monitor Metrics
```bash
# Start Prometheus and Grafana
docker-compose up -d prometheus grafana

# Access Grafana
open http://localhost:3000
```

### Check Circuit Breaker Status
```bash
curl http://localhost:8080/admin/circuit-breakers \
  -H "Authorization: Bearer $ADMIN_TOKEN"
```

### View Structured Logs
```bash
# Logs are in JSON format
tail -f logs/application.log | jq .
```

### Expected Behavior
- Rate limiting returns 429 with retry-after header
- Circuit breakers open after threshold failures
- Structured logs include correlation IDs
- Metrics track golden signals (latency, traffic, errors, saturation)
- Concurrency tests prove no double-bookings
- Load tests meet performance SLAs

---

## Next Steps

### Immediate
- [ ] Review and optimize transaction boundaries
- [ ] Implement concurrency control mechanisms
- [ ] Add rate limiting to all endpoints
- [ ] Implement circuit breakers for external services
- [ ] Add structured JSON logging
- [ ] Create load and concurrency tests
- [ ] Perform security audit (OWASP API Top 10)
- [ ] Optimize database queries and indexes
- [ ] Add golden signals metrics
- [ ] Create operational runbooks

### Phase 7: Deployment
1. Configure Kubernetes for production
2. Set up CI/CD pipelines
3. Implement blue-green deployments
4. Configure monitoring and alerting
5. Set up backup and disaster recovery

### Future Enhancements
- Auto-scaling based on metrics
- Advanced caching strategies
- Database read replicas
- Geographic distribution

---

## References

### Project Documentation
- `fleet-management-plan.md` - Overall project plan
- `phase-5-reporting-and-accounting-correctness.md` - Next phase
- `phase-6-deployment.md` - Deployment phase

### Skills Documentation
- `skills/backend-development/SKILL.md` - Backend principles
- `skills/clean-code/SKILL.md` - Coding standards
- `skills/api-patterns/rate-limiting.md` - Rate limiting strategies
- `skills/security/SKILL.md` - Security best practices (if exists)
- `skills/performance/SKILL.md` - Performance optimization (if exists)

### Hardening Documentation
- `docs/runbooks/incident-response.md` - Incident procedures (to be created)
- `docs/runbooks/performance-tuning.md` - Performance guide (to be created)
- `docs/security/owasp-checklist.md` - Security checklist (to be created)

---

## Summary

**Phase 4 Status**: **In Progress**

This phase will harden the system for production use with concurrency control, rate limiting, circuit breakers, structured logging, and comprehensive testing.

**Key Deliverables**:
- [ ] Concurrency control (optimistic/pessimistic locking)
- [ ] Rate limiting on all endpoints
- [ ] Circuit breakers for external services
- [ ] Structured JSON logging
- [ ] Performance optimization (indexes, caching)
- [ ] Load and concurrency tests
- [ ] Security audit (OWASP API Top 10)
- [ ] Golden signals metrics
- [ ] Operational runbooks
- [ ] Failure mode testing

**Ready for Phase 5**: Not Yet

Once Phase 5 is complete, the system will be technically robust for the accounting layer and eventual deployment.

---

**Implementation Date**: TBD  
**Verification**: Pending  
**Hardening Status**: Not Started  
**Compliance**: 0%  
**Ready for Next Phase**: Not Yet
