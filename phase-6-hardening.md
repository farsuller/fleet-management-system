# Phase 6 — Hardening

## Status

- Overall: **Not Started**
- Implementation Date: TBD
- Verification: Pending

---

## Purpose

Improve correctness, reliability, and performance under real-world concurrency and load.

---

## Depends on

- Phases 1–5 (baseline functionality exists)

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
| Rate limiting | Not Started | Apply 429 + headers; prevent brute force/resource exhaustion |
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

#### 1. **Concurrency Control**
**Optimistic Locking**:
```kotlin
// Version-based optimistic locking
data class Vehicle(
    val id: UUID,
    val version: Long,  // Incremented on each update
    // ... other fields
)

fun updateVehicle(vehicle: Vehicle) {
    val updated = transaction {
        val current = vehicleRepository.findById(vehicle.id)
        require(current.version == vehicle.version) {
            "Concurrent modification detected"
        }
        vehicleRepository.update(vehicle.copy(version = vehicle.version + 1))
    }
}
```

**Pessimistic Locking**:
```kotlin
// Advisory locks for critical sections
fun reserveVehicle(vehicleId: UUID, rental: Rental) {
    transaction {
        // Acquire lock
        connection.execute("SELECT pg_advisory_xact_lock(?)", vehicleId.hashCode())
        
        // Check availability
        val conflicts = rentalRepository.findOverlapping(vehicleId, rental.period)
        require(conflicts.isEmpty()) { "Vehicle not available" }
        
        // Create reservation
        rentalRepository.save(rental)
    }
}
```

#### 2. **Rate Limiting**
**Implementation**:
```kotlin
// Token bucket algorithm using Redis
class RateLimiter(private val redis: RedisClient) {
    fun checkLimit(userId: String, limit: Int, windowSeconds: Int): Boolean {
        val key = "rate_limit:$userId"
        val current = redis.incr(key)
        
        if (current == 1L) {
            redis.expire(key, windowSeconds)
        }
        
        return current <= limit
    }
}

// Apply to routes
install(RateLimiting) {
    limit = 100  // requests
    window = 60  // seconds
    onExceeded = {
        call.respond(
            HttpStatusCode.TooManyRequests,
            ApiResponse.error(
                code = "RATE_LIMIT_EXCEEDED",
                message = "Too many requests",
                requestId = call.requestId
            )
        )
    }
}
```

#### 3. **Structured Logging**
**JSON Log Format**:
```kotlin
// Structured logging with correlation
logger.info(
    mapOf(
        "event" to "rental_created",
        "requestId" to requestId,
        "userId" to userId,
        "rentalId" to rentalId,
        "vehicleId" to vehicleId,
        "amount" to amount,
        "timestamp" to Clock.System.now()
    )
)
```

#### 4. **Circuit Breakers**
**Implementation**:
```kotlin
// Circuit breaker for external services
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeout: Duration = 60.seconds
) {
    private var failureCount = 0
    private var state = State.CLOSED
    private var lastFailureTime: Instant? = null
    
    suspend fun <T> execute(block: suspend () -> T): T {
        when (state) {
            State.OPEN -> {
                if (shouldAttemptReset()) {
                    state = State.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException()
                }
            }
            State.HALF_OPEN, State.CLOSED -> {
                // Attempt execution
            }
        }
        
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

#### 5. **Performance Optimization**
**Query Optimization**:
- Add indexes for common query patterns
- Use database connection pooling
- Implement query result caching
- Eliminate N+1 queries with eager loading

**Caching Strategy**:
```kotlin
// Cache frequently accessed data
class VehicleCache(private val redis: RedisClient) {
    suspend fun getVehicle(id: UUID): Vehicle? {
        val cached = redis.get("vehicle:$id")
        if (cached != null) {
            return Json.decodeFromString(cached)
        }
        
        val vehicle = vehicleRepository.findById(id)
        if (vehicle != null) {
            redis.setex("vehicle:$id", 300, Json.encodeToString(vehicle))
        }
        
        return vehicle
    }
}
```

---

## Verification

### Load Test Results

*This section will be populated with:*
- Concurrent request handling
- Double-booking prevention under load
- Rate limiting effectiveness
- Circuit breaker behavior
- Performance benchmarks
- Security audit results

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

## Compliance Status

### Phase 1-5 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Core functionality | ✅ | All features implemented |
| Database layer | ✅ | Schemas and repositories |
| API layer | ✅ | REST endpoints |
| Event layer | ✅ | Kafka integration |
| Accounting | ✅ | Ledger and reports |

### Phase 6 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Transaction boundaries | Not Started | Review and optimize |
| Concurrency control | Not Started | Locking strategies |
| Double-booking tests | Not Started | Load testing |
| Kafka tuning | Not Started | Backpressure handling |
| Redis audit | Not Started | TTL and usage review |
| Rate limiting | Not Started | Implementation |
| Security hardening | Not Started | OWASP checklist |
| Observability | Not Started | Golden signals |
| Performance tuning | Not Started | Index optimization |
| Failure-mode testing | Not Started | Resilience validation |
| Structured logging | Not Started | JSON format |
| Circuit breakers | Not Started | Implementation |

**Overall Compliance**: **0%** (Not Started)

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
- `phase-5-reporting-and-accounting-correctness.md` - Previous phase
- `phase-7-deployment.md` - Next phase

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

**Phase 6 Status**: **Not Started**

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

**Ready for Phase 7**: Not Yet

Once Phase 6 is complete, the system will be production-ready for deployment to Kubernetes.

---

**Implementation Date**: TBD  
**Verification**: Pending  
**Hardening Status**: Not Started  
**Compliance**: 0%  
**Ready for Next Phase**: Not Yet
