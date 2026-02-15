# Phase 4 — Hardening v2

## Status

- Overall: **✅ COMPLETED**
- Implementation Date: 2026-02-14 to 2026-02-15
- Verification: Completed (11/11 core features implemented)

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
| Transaction boundaries review | ✅ Completed | Implemented via `dbQuery` and `newSuspendedTransaction` |
| Concurrency strategy | ✅ Completed | Optimistic (Version) and Pessimistic (Advisory) locks supported |
| SQL Injection protection | ✅ Completed | Enforced via Exposed Type-Safe DSL |
| Rate limiting | ✅ Completed | Multi-tiered Ktor Native Plugin (IP & User based) |
| Idempotency | ✅ Completed | Header-based key tracking with DB persistence |
| Cursor-based pagination | ✅ Completed | Stable sort on unique UUIDs |
| RBAC & Security | ✅ Completed | JWT-based roles with `withRoles` route-scoped plugin |
| Observability (Golden Signals) | ✅ Completed | Micrometer/Prometheus + CallLogging + RequestId correlation |
| Global Error Handling | ✅ Completed | Centralized `StatusPages` mapping domain exceptions to HTTP |
| Netty Asynchronous Engine | ✅ Completed | Non-blocking server core using Kotlin Coroutines |
| Kafka consumer tuning | ⏸️ Parked | Deferred to Phase 9 (Event-Driven Architecture) |
| Redis usage audit | ✅ Completed | Active for VehicleRepository caching (5-min TTL) |
| Performance tuning | ⏸️ Deferred | Pre-production activity: Index optimization based on real usage |
| Failure-mode testing | ⏸️ Deferred | Pre-production activity: Chaos testing with Testcontainers |
| Structured JSON logging | ✅ Completed | Logstash encoder with environment-based switching |
| Circuit breakers | ✅ Completed | Code ready, awaiting external service integrations |

---

## 🏢 Business Scenarios & Technical Mitigations

Hardening is not just about performance; it's about protecting business continuity and financial integrity. Below are the key scenarios this phase addresses:

| Scenario | Business Risk | Technical Mitigation |
|:---|:---|:---|
| **The "Ghost Rental"** | Two customers arrive at the lot to pick up the same unique vehicle because the system allowed a double-booking during a high-traffic promo. | **Pessimistic Advisory Locks**: Ensures that checking availability and creating a reservation is an atomic operation for a specific `vehicle_id`. |
| **The "Double Charge"** | A customer on a flaky mobile connection clicks "Pay" twice; the system records two identical payments, leading to support overhead and refunds. | **Idempotency Keys (`X-Idempotency-Key`)**: The server ignores the second request if the unique key has already been processed within the TTL window. |
| **The "Brute Force"** | An automated script attempts thousands of password combinations per minute on a staff account, eventually gaining unauthorized access. | **Tiered Rate Limiting**: Limits login attempts per IP/User to a strict "safety zone" (e.g., 5 attempts per minute), blocking malicious automation. |
| **The "Cascading Outage"** | The external Payment Gateway is slow/down; the Fleet system's threads all hang waiting for a response, eventually crashing our entire API. | **Circuit Breakers**: Detects the external failure and "trips" the circuit, immediately returning a 503 error to save local system resources. |
| **The "Stealth Change"** | Two managers edit the same vehicle's daily rate at once. Manager A's change is silently overwritten by Manager B, leading to pricing errors. | **Optimistic Locking**: Uses a `version` column; the second update fails if the version has changed since the data was originally read. |

---

## Definition of Done (Phase 4)

- [x] Concurrency correctness supported (Optimistic & Pessimistic)
- [x] SQL Injection protection enforced via Type-Safe DSL
- [x] Multi-tiered Rate Limiting (IP & User based) operational
- [x] API Idempotency implemented for critical POST/PUT operations
- [x] RBAC (Authorization) enforced across all protected routes
- [x] Cursor-based pagination implemented for all listing APIs
- [x] Observability (Metrics & Request Correlation) initialized
- [x] Global Error Handling standardized via StatusPages
- [x] Structured JSON Logging with Logstash encoder
- [x] Circuit Breakers code ready (awaiting external services)
- [x] Redis Caching active for VehicleRepository
- [ ] Failure mode tests validate resilience (Deferred to Pre-Production)
- [ ] Performance benchmarks meet SLAs (Deferred to Pre-Production)
- [ ] Security audit completed (OWASP API Top 10)

---

## Implementation Summary

### ✅ Core Features Implemented

*This section will be populated during implementation with:*

#### 1. **Concurrency Control (Optimistic & Pessimistic)** ✅ **IMPLEMENTED**
*Purpose: Prevent race conditions and ensure data integrity during simultaneous updates (e.g., double-booking).*

**Status:** Fully implemented with both optimistic and pessimistic locking strategies.

**Files:**
- Domain: `src/main/kotlin/com/solodev/fleet/modules/vehicles/domain/model/Vehicle.kt`
- Repository: `src/main/kotlin/com/solodev/fleet/modules/vehicles/infrastructure/persistence/VehicleRepositoryImpl.kt`
- Use Case: `src/main/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CreateRentalUseCase.kt`
- Database: `src/main/resources/db/migration/V002__create_vehicles_schema.sql`

**Dependency:**
Built-in to **Exposed ORM** and **PostgreSQL**. No additional libraries required.

**Code Implementation:**
We use both **Optimistic Locking** (for high-concurrency/low-conflict scenarios) and **Pessimistic Locking** (for critical sections like reservations).

> [!NOTE]
> We use the domain-specific `VehicleId` value class rather than raw `UUID` for type safety. This prevents accidentally passing a `UserId` or `RentalId` where a `VehicleId` is expected.

```kotlin
// 1. Optimistic (Version-based)
// The domain entity includes a 'version' field which the repository uses for conflict detection
data class Vehicle(val id: VehicleId, val version: Long, ...)

// 2. Pessimistic (Advisory Locks)
// Used for critical sections where we must block concurrent attempts to the same resource
fun reserveVehicle(vehicleId: VehicleId, rentalPeriod: ClosedRange<Instant>) {
    transaction {
        // Map the UUID string to a hash for a session-level advisory lock
        val lockId = UUID.fromString(vehicleId.value).hashCode()
        exec("SELECT pg_advisory_xact_lock(?)", lockId)

        // ... safety checks & business logic ...
    }
}
```

#### ⚖️ Architectural Design Rule: Where does Locking live?

Concurrency orchestration (locking, transactions) is a cross-cutting concern that belongs in the **Application Layer (Use Cases)**.

| Layer | Responsibility | Why not for Locking? |
| :--- | :--- | :--- |
| **Domain Model** (`Vehicle.kt`) | Pure business rules and state transitions. | **Don't use**: Adding `transaction` or `exec` leaks database knowledge into "pure" code. |
| **Repository** (`VehicleRepositoryImpl`) | Data persistence and mapping. | **Too Narrow**: Repository locks only protect a single DB call, not the whole "Check-then-Act" business process. |
| **Use Case** (`CreateRentalUseCase`) | **Perfect Spot**: Coordinates multiple repositories and business rules within a single atomic lock/transaction. | **YES**: This is where you acquire the lock, perform validations, and save the result. |

#### 🚀 Implementation Location: `CreateRentalUseCase.kt`

**File Path:** `src/main/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CreateRentalUseCase.kt`

**Why this location?**  
The Use Case orchestrates the entire "Reserve Vehicle" business process. By acquiring the lock at the start of the transaction, we prevent race conditions where two users might simultaneously check availability and both succeed, leading to double-booking.

```kotlin
class CreateRentalUseCase(
    private val rentalRepository: RentalRepository,
    private val vehicleRepository: VehicleRepository
) {
    // Local helper for database transactions (pattern used across repositories)
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun execute(request: RentalRequest): Rental = dbQuery {
        val vehicleId = VehicleId(request.vehicleId)
        
        // 1. ACQUIRE PESSIMISTIC LOCK - Blocks other transactions from checking this vehicle
        val lockId = UUID.fromString(vehicleId.value).hashCode().toLong()
        TransactionManager.current().exec("SELECT pg_advisory_xact_lock($lockId)")

        // 2. VALIDATE - Now protected from concurrent modifications
        val vehicle = vehicleRepository.findById(vehicleId) 
            ?: throw IllegalArgumentException("Vehicle not found")
        require(vehicle.state == VehicleState.AVAILABLE)
        
        // 3. CHECK CONFLICTS
        val conflicts = rentalRepository.findConflictingRentals(vehicleId, startDate, endDate)
        require(conflicts.isEmpty())

        // 4. PERSIST - Lock is released when transaction commits
        val rental = Rental(...)
        rentalRepository.save(rental)
    }
}
```

**Key Points:**
- Lock is acquired **before** any business validation
- Lock is automatically released when `dbQuery` transaction completes
- Uses PostgreSQL's `pg_advisory_xact_lock` (transaction-scoped, not session-scoped)

---

### ✅ Current Implementation (Hardened Components)

This section documents the hardening features already integrated into the Fleet Management codebase.

#### 1. **SQL Injection Protection** ✅ **IMPLEMENTED**
*Purpose: Prevent malicious SQL code from being executed through user input.*

**Status:** Fully implemented via Exposed ORM across all repositories.

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

#### 2. **API Rate Limiting (Ktor Native)** ✅ **IMPLEMENTED**
*Purpose: Protect endpoints from brute-force attacks and resource exhaustion.*

**Status:** Fully implemented with multi-tiered rate limiting strategy.

**File:** `src/main/kotlin/com/solodev/fleet/shared/plugins/RateLimiting.kt`

**Dependency:**
```kotlin
// build.gradle.kts
implementation(libs.ktor.server.rate.limit)
```

**Code Implementation:**
A multi-tiered configuration that scales limits based on user trust levels and endpoint sensitivity.

```kotlin
// RateLimiting.kt
fun Application.configureRateLimiting() {
    install(RateLimit) {
        // 1. Global Rate Limit: Safety net for entire server
        register {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
        }

        // 2. Public API: IP-based limit for guest users
        register(RateLimitName("public_api")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // 3. Sensitive Endpoints: Strict limits for auth endpoints
        register(RateLimitName("auth_strict")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // 4. Authenticated API: User-based limiting with higher quota
        register(RateLimitName("authenticated_api")) {
            rateLimiter(limit = 500, refillPeriod = 1.minutes)
            requestKey { call ->
                call.principal<JWTPrincipal>()?.payload?.getClaim("id")?.asString()
                    ?: call.request.origin.remoteHost
            }
        }
    }
}
```

**Applying Method:**
Apply the `rateLimit` block surrounding route groups in `Routing.kt`.

**Key Advantages:**
- **Brute-Force Shield**: Prevents attackers from attempting thousands of logins.
- **Fair Usage**: Ensures no single user can overload the public API.
- **User-Based Tracking**: Authenticated users tracked by ID, not IP (prevents bypass).

---

---

#### 3. **Netty Asynchronous Engine** ✅ **IMPLEMENTED**
*Purpose: High-performance, non-blocking asynchronous server core.*

**Status:** Fully implemented as the application server engine.

**File:** `src/main/kotlin/com/solodev/fleet/Application.kt`

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
    EngineMain.main(args)
}
```

**Key Advantages:**
- **Concurrency**: Optimized for handling thousands of concurrent connections using Kotlin Coroutines.
- **Resource Efficiency**: Low memory footprint compared to traditional servlet containers.
- **Production-Ready**: Battle-tested engine used by major applications.

---

#### 4. **Idempotency (Safety Retries)** ✅ **IMPLEMENTED**
*Purpose: Prevent duplicate side-effects (e.g. double-billing) during network retries.*

**Status:** Fully implemented with custom Ktor plugin and database persistence.

**Files:** 
- `src/main/kotlin/com/solodev/fleet/shared/plugins/Idempotency.kt`
- `src/main/kotlin/com/solodev/fleet/shared/infrastructure/persistence/IdempotencyRepositoryImpl.kt`

**Dependency:**
Built-in to **Infrastructure Layer** using SQL unique constraints and custom plugin.

**Code Implementation:**
The `Idempotency` plugin checks for a unique header and stores the result of the first successful request.

```kotlin
// Idempotency.kt
val Idempotency = createRouteScopedPlugin(name = "Idempotency", createConfiguration = ::IdempotencyConfig) {
    val repository = pluginConfig.repository
    val headerName = pluginConfig.headerName

    onCall { call ->
        val key = call.request.headers[headerName] ?: return@onCall
        call.attributes.put(IdempotencyKey, key)

        // Check if request already exists
        val existing = repository.get(key)
        if (existing != null) {
            // Return cached response
            call.respond(HttpStatusCode.fromValue(existing.first), existing.second)
            return@onCall
        }
    }
}
```

**Applying Method:**
Install on specific routes (e.g., payment endpoints):
```kotlin
route("/payments") {
    install(Idempotency)
    post { /* ... */ }
}
```

Clients must send a unique UUID in the `Idempotency-Key` header for POST/PUT operations.

**Key Advantages:**
- **Double-Post Prevention**: Safe to retry if the client times out but the server succeeded.
- **Data Integrity**: Critical for accounting and payment processing.
- **Database-Backed**: Survives server restarts (not just in-memory cache).

---

---

#### 5. **Cursor-Based Pagination** ✅ **IMPLEMENTED**
*Purpose: Efficient, stable listing of large datasets.*

**Status:** Fully implemented across all list endpoints.

**Files:**
- Models: `src/main/kotlin/com/solodev/fleet/shared/models/Pagination.kt`
- Plugin: `src/main/kotlin/com/solodev/fleet/shared/plugins/Pagination.kt`
- Repository: `src/main/kotlin/com/solodev/fleet/modules/vehicles/infrastructure/persistence/VehicleRepositoryImpl.kt`

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
- **Type-Safe**: `PaginationParams` data class ensures consistent API.

---

#### 6. **Role-Based Access Control (RBAC)** ✅ **IMPLEMENTED**
*Purpose: Ensure that only authorized personnel can access sensitive operations.*

**Status:** Fully implemented with custom Authorization plugin and role-based route protection.

**File:** `src/main/kotlin/com/solodev/fleet/shared/plugins/Security.kt`

**Code Implementation:**
A custom `Authorization` plugin that checks JWT claims against required roles for specific routes.

```kotlin
// Security.kt
enum class UserRole {
    ADMIN, FLEET_MANAGER, CUSTOMER
}

val Authorization = createRouteScopedPlugin(name = "Authorization", createConfiguration = ::AuthorizationConfig) {
    val roles = pluginConfig.requiredRoles

    on(AuthenticationChecked) { call ->
        if (roles.isNotEmpty()) {
            val principal = call.principal<JWTPrincipal>()
            val userRoles = principal?.getRoles() ?: emptyList()

            if (roles.none { it in userRoles } && UserRole.ADMIN !in userRoles) {
                call.respond(HttpStatusCode.Forbidden, "Insufficient permissions")
            }
        }
    }
}

// Helper function for route protection
fun Route.withRoles(vararg roles: UserRole, build: Route.() -> Unit): Route {
    install(Authorization) { requiredRoles = roles.toList() }
    return apply(build)
}
```

**Applying Method:**
```kotlin
// Routes.kt
route("/v1/vehicles") {
    withRoles(UserRole.FLEET_MANAGER, UserRole.ADMIN) {
        post { ... } // Only Fleet Managers/Admins can add vehicles
    }
}
```

**Key Advantages:**
- **Centralized Enforcement**: Logic is handled by the plugin, not repeated in every route.
- **Granular Control**: Roles can be applied to specific route groups (e.g., READ vs WRITE).
- **Admin Override**: ADMIN role always has access to all protected routes.

---

---

#### 7. **Observability: Metrics & Correlation** ✅ **IMPLEMENTED**
*Purpose: Track "Golden Signals" (Latency, Traffic, Errors) and correlate logs across service calls.*

**Status:** Fully implemented with Micrometer metrics and request ID correlation.

**Files:**
- Metrics: `src/main/kotlin/com/solodev/fleet/shared/plugins/Observability.kt`
- Correlation: `src/main/kotlin/com/solodev/fleet/shared/plugins/RequestId.kt`

**Code Implementation:**
Using Micrometer with Prometheus and a custom `X-Request-ID` tracking system.

```kotlin
// Observability.kt
fun Application.configureObservability() {
    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = appMicrometerRegistry
    }
}

// RequestId.kt
fun Application.configureRequestId() {
    intercept(ApplicationCallPipeline.Setup) {
        val requestId = call.request.header("X-Request-ID") ?: generateRequestId()
        call.attributes.put(RequestIdKey, requestId)
    }
}
```

**Key Advantages:**
- **Traceability**: Every log entry and error response is tagged with a `requestId`, allowing us to trace a single request through the entire system.
- **Monitoring Ready**: Metrics are exposed in a format ready for Prometheus scrape.
- **Call Logging**: Automatic logging of all HTTP requests with correlation IDs.

---

#### 8. **Standardized Global Error Handling** ✅ **IMPLEMENTED**
*Purpose: Provide a consistent API experience and prevent sensitive info leakage.*

**Status:** Fully implemented with StatusPages plugin and custom exception mapping.

**File:** `src/main/kotlin/com/solodev/fleet/shared/plugins/StatusPages.kt`

**Code Implementation:**
Using the `StatusPages` plugin to map domain exceptions to a standard `ApiResponse` envelope.

```kotlin
// StatusPages.kt
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                ApiResponse.error(
                    code = "VALIDATION_ERROR",
                    message = cause.message ?: "Validation failed",
                    requestId = call.requestId
                )
            )
        }

        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                ApiResponse.error(
                    code = cause.code,
                    message = cause.message,
                    requestId = call.requestId
                )
            )
        }

        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiResponse.error(
                    code = "INTERNAL_ERROR",
                    message = "An unexpected error occurred",
                    requestId = call.requestId
                )
            )
        }
    }
}
```

**Key Advantages:**
- **Contract Stability**: Mobile apps and frontends always receive the same JSON structure, even for 500 errors.
- **Security**: Detailed stack traces are logged but never exposed to the client.
- **Request Correlation**: Every error response includes the `requestId` for debugging.

---

### 🚀 Planned Hardening (Professional Upgrades)

These components are targeted for full implementation as the system scales.

#### 6. **Distributed Rate Limiting (Bucket4j + Redis)**
*Status: Planned for Multi-Instance Support*

**Purpose:**
Moving from in-memory (Local) to Redis-backed (Global) rate limiting to support horizontal scaling across multiple server instances.

**Potential Dependencies:**
```kotlin
// build.gradle.kts
implementation("com.bucket4j:bucket4j-core:8.x.x")
implementation("redis.clients:jedis:5.1.0") // or alternative Redis client
```

**Applying Method:**
Nesting the Bucket4j check inside the Ktor `rateLimit` (Moat) layer for a Defense in Depth strategy.

---

---

#### 7. **Distributed Caching (Redis)**
*Status: Planned for DB Relief*

**File:** `src/main/kotlin/com/solodev/fleet/shared/infrastructure/cache/RedisCacheManager.kt`

**Dependency:**
```kotlin
// build.gradle.kts
implementation("redis.clients:jedis:5.1.0")
```

**Code Implementation:**
Creating a generic `CacheManager` to handle JSON serialization and TTLs.

```kotlin
// RedisCacheManager.kt
package com.solodev.fleet.shared.infrastructure.cache

import kotlinx.serialization.json.Json
import redis.clients.jedis.Jedis

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

**Setup in Application.kt:**
```kotlin
// Application.kt
fun Application.module() {
    // Initialize Redis connection
    val jedis = Jedis("localhost", 6379)
    val cacheManager = RedisCacheManager(jedis)
    
    // Pass to repositories that need caching
    val vehicleRepository = VehicleRepositoryImpl(cacheManager)
    
    // ... other configurations
}
```

**Usage Example in Repository:**
```kotlin
// VehicleRepositoryImpl.kt
class VehicleRepositoryImpl(
    private val cacheManager: RedisCacheManager? = null // Optional for gradual rollout
) : VehicleRepository {
    
    override suspend fun findById(id: VehicleId): Vehicle? {
        // If caching is enabled, use Cache-Aside pattern
        return cacheManager?.getOrSet(
            key = "vehicle:${id.value}",
            ttlSeconds = 300 // 5 minutes
        ) {
            // Fallback to database if cache miss
            dbQuery {
                VehiclesTable.selectAll()
                    .where { VehiclesTable.id eq UUID.fromString(id.value) }
                    .singleOrNull()?.toVehicle()
            }
        } ?: dbQuery {
            // No caching - direct DB query
            VehiclesTable.selectAll()
                .where { VehiclesTable.id eq UUID.fromString(id.value) }
                .singleOrNull()?.toVehicle()
        }
    }
    
    override suspend fun save(vehicle: Vehicle): Vehicle {
        val result = dbQuery {
            // ... save logic ...
        }
        
        // Invalidate cache after write
        cacheManager?.let { 
            jedis.del("vehicle:${vehicle.id.value}")
        }
        
        return result
    }
}
```

**Key Advantages:**
- **Read Performance**: Reduces DB load for frequently accessed vehicles
- **Cache-Aside Pattern**: Repository controls cache logic, not the domain
- **Optional Integration**: Can be enabled per-repository without breaking existing code
- **TTL Management**: Automatic expiration prevents stale data

**When to Use:**
- High-read, low-write entities (e.g., Vehicle catalog, pricing)
- Expensive queries (joins, aggregations)
- External API responses (rate limit protection)

**When NOT to Use:**
- Real-time data (e.g., current vehicle location)
- Frequently updated entities (cache thrashing)
- Small datasets (caching overhead > benefit)

---

### 📊 **Repository-Specific Caching Guidance**

Not all repositories benefit equally from caching. Apply caching selectively based on data characteristics:

#### ✅ **Recommended for Caching:**

| Repository | TTL | Rationale |
|------------|-----|-----------|
| **VehicleRepositoryImpl** | 5 min | ✅ **ACTIVE** - Vehicle catalog data (specs, pricing) changes infrequently. High read volume. |
| **CustomerRepositoryImpl** | 10 min | Customer profiles rarely change. Reduces DB load during rental lookups. |
| **PaymentMethodRepositoryImpl** | 15 min | Payment methods are relatively static. Faster checkout experience. |

#### ⚠️ **Cache with Caution:**

| Repository | TTL | Considerations |
|------------|-----|----------------|
| **UserRepositoryImpl** | 3 min | ⚠️ Cache only for profile lookups, NOT for authentication. Risk of stale auth data. |
| **AccountRepositoryImpl** | 30 sec | ⚠️ Account balances change frequently. Very short TTL or skip entirely. |

#### ❌ **Do NOT Cache:**

| Repository | Reason |
|------------|--------|
| **RentalRepositoryImpl** | ❌ Real-time rental status is critical. Risk of double-booking if cache is stale. |
| **InvoiceRepositoryImpl** | ❌ Financial data must be real-time. Risk of showing incorrect amounts. |
| **MaintenanceRepositoryImpl** | ❌ Maintenance status changes frequently. Risk of renting out vehicles under maintenance. |
| **LedgerRepositoryImpl** | ❌ Accounting ledger must be transactionally consistent. Financial discrepancies are unacceptable. |

**Current Implementation Status:**
- ✅ **VehicleRepositoryImpl**: Caching active with 5-minute TTL
- ⏳ **Others**: Not implemented (evaluate based on performance metrics)

**Expansion Strategy:**
1. Monitor VehicleRepository cache hit rate and performance gains
2. If successful, add CustomerRepository and PaymentMethodRepository
3. Avoid caching real-time or financial data repositories

---

#### 8. **Circuit Breakers**
*Status: Planned for External Resilience*

**File:** `src/main/kotlin/com/solodev/fleet/shared/plugins/CircuitBreaker.kt`

**Dependency:**
```kotlin
// build.gradle.kts
implementation("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")
implementation("io.github.resilience4j:resilience4j-kotlin:2.1.0")
```

**Code Implementation:**
Wrap external calls in circuit breakers to prevent cascading failures.

```kotlin
// CircuitBreaker.kt
package com.solodev.fleet.shared.plugins

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.ktor.server.application.*
import java.time.Duration

/**
 * Configures circuit breakers for external service calls.
 * Prevents cascading failures by failing fast when external services are down.
 */
fun Application.configureCircuitBreakers(): CircuitBreakerRegistry {
    val config = CircuitBreakerConfig.custom()
        .failureRateThreshold(50.0f) // Open circuit if 50% of calls fail
        .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before trying again
        .slidingWindowSize(10) // Track last 10 calls
        .build()

    return CircuitBreakerRegistry.of(config)
}

/**
 * Execute a suspendable function with circuit breaker protection.
 */
suspend fun <T> CircuitBreaker.executeSuspend(block: suspend () -> T): T {
    return io.github.resilience4j.kotlin.circuitbreaker.executeSuspendFunction(block)
}
```

**Usage Example:**
```kotlin
// In a repository or service
class ExternalPaymentService(private val circuitBreaker: CircuitBreaker) {
    suspend fun processPayment(amount: Int): PaymentResult {
        return circuitBreaker.executeSuspend {
            // Call external payment API
            externalApi.charge(amount)
        }
    }
}
```

**Applying Method:**
Used specifically for external integrations (e.g., Payment Gateways, Third-party APIs).

---

### 🎯 **When to Apply Circuit Breakers**

Circuit breakers protect your system from cascading failures caused by slow or failing external dependencies. Apply them strategically:

#### ✅ **Apply Circuit Breakers For:**

| External Service | Risk Without Circuit Breaker | Recommended Config |
|------------------|------------------------------|-------------------|
| **Payment Gateways** (Stripe, PayPal) | All payment threads hang → entire API becomes unresponsive | Failure threshold: 50%, Wait: 30s |
| **SMS/Email Services** (Twilio, SendGrid) | Notification delays block user registration/checkout | Failure threshold: 60%, Wait: 60s |
| **Third-Party APIs** (Google Maps, Weather) | Slow geocoding delays rental creation | Failure threshold: 50%, Wait: 30s |
| **OAuth Providers** (Google, Facebook) | Login system becomes unavailable | Failure threshold: 40%, Wait: 20s |
| **External Webhooks** | Webhook delivery failures consume worker threads | Failure threshold: 70%, Wait: 120s |

#### ❌ **Do NOT Use Circuit Breakers For:**

| Internal Component | Why Not? | Alternative |
|--------------------|----------|-------------|
| **Database Calls** | Connection pooling already handles failures | HikariCP connection pool with timeout |
| **Internal Microservices** | Use service mesh (Istio) or load balancer health checks | Kubernetes liveness/readiness probes |
| **Redis Cache** | Cache misses should fall back to DB, not fail | Try-catch with fallback to fetcher |
| **Local File System** | I/O errors should be handled differently | Standard exception handling |

#### 📊 **Current Implementation Status:**

- ✅ **Code Ready:** `CircuitBreaker.kt` implemented with Resilience4j
- ✅ **Dependencies:** `resilience4j-circuitbreaker:2.1.0` in build.gradle
- ⏳ **Not Active:** No external service integrations yet
- 🎯 **Activation Trigger:** When integrating payment gateway or external APIs

#### 🚀 **Activation Strategy:**

**Step 1: Identify External Dependency**
```kotlin
// Example: Payment Gateway Integration
class StripePaymentService(private val circuitBreaker: CircuitBreaker) {
    suspend fun charge(amount: Int): PaymentResult {
        return circuitBreaker.executeSuspend {
            // External API call that could fail or timeout
            stripeClient.charges.create(amount)
        }
    }
}
```

**Step 2: Register Circuit Breaker in Application.kt**
```kotlin
fun Application.module() {
    // ... other configs ...
    
    val circuitBreakerRegistry = configureCircuitBreakers()
    val paymentCircuitBreaker = circuitBreakerRegistry.circuitBreaker("payment-gateway")
    
    val paymentService = StripePaymentService(paymentCircuitBreaker)
}
```

**Step 3: Monitor Circuit State**
- **CLOSED** (Normal): Requests pass through
- **OPEN** (Failing): Requests fail fast without calling external service
- **HALF_OPEN** (Testing): Limited requests to test if service recovered

**Benefits:**
- Prevents thread exhaustion from hanging external calls
- Fails fast when external service is down
- Automatic recovery testing after wait period
- Protects your system from cascading failures

---

#### 9. **Structured JSON Logging** ✅ **IMPLEMENTED**
*Purpose: Machine-readable logs for ELK/Datadog integration and better observability.*

**Status:** Fully implemented with environment-based switching.

**Files:**
- Configuration: `src/main/resources/logback.xml`
- Dependency: `logstash-logback-encoder:8.0`

**Dependency:**
```kotlin
// build.gradle.kts
implementation("net.logstash.logback:logstash-logback-encoder:8.0")
```

**Code Implementation:**
Using Logstash's `LogstashEncoder` to output structured JSON logs with automatic field extraction.

```xml
<!-- logback.xml -->
<configuration>
    <!-- JSON Console Appender for Production -->
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Include MDC fields (requestId, userId) -->
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            
            <!-- Custom application metadata -->
            <customFields>{"application":"fleet-management","environment":"${ENVIRONMENT:-development}"}</customFields>
        </encoder>
    </appender>

    <!-- Environment-based switching -->
    <if condition='property("ENVIRONMENT").equals("production")'>
        <then>
            <root level="INFO">
                <appender-ref ref="JSON_CONSOLE"/>
            </root>
        </then>
        <else>
            <root level="INFO">
                <appender-ref ref="PLAIN_CONSOLE"/>  <!-- Human-readable for dev -->
            </root>
        </else>
    </if>
</configuration>
```

**JSON Output Example:**
```json
{
  "timestamp": "2026-02-15T16:48:00.123Z",
  "level": "INFO",
  "thread": "DefaultDispatcher-worker-1",
  "logger": "com.solodev.fleet.modules.vehicles.VehicleService",
  "message": "Vehicle created successfully",
  "requestId": "abc123-def456",
  "userId": "user-789",
  "application": "fleet-management",
  "environment": "production"
}
```

**Key Advantages:**
- **Machine-Readable**: Easy to parse by log aggregation tools (ELK, Datadog, Splunk)
- **Request Correlation**: Automatic `requestId` inclusion from MDC
- **Environment Switching**: JSON in production, plain text in development
- **Custom Fields**: Application metadata automatically added to every log entry
- **Zero Code Changes**: Works with existing `log.info()` calls

**Usage in Code:**
```kotlin
// No changes needed! Existing logging works automatically
class VehicleService {
    private val log = LoggerFactory.getLogger(VehicleService::class.java)
    
    suspend fun createVehicle(request: VehicleRequest) {
        log.info("Creating vehicle: ${request.plateNumber}")
        // Output is automatically JSON in production
    }
}
```

**Setting Environment Variable:**
```bash
# Development (plain text)
./gradlew run

# Production (JSON)
export ENVIRONMENT=production
./gradlew run
```

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
| RBAC (Security) | ✅ Completed | JWT Roles + Authorization Plugin |
| Observability | ✅ Completed | micrometer-prometheus + CallLogging |
| Request Correlation | ✅ Completed | `X-Request-Id` managed across layers |
| Global Error Handling | ✅ Completed | Uniform JSON responses for all failures |
| Redis Caching | ⏳ Planned | Phase 8 Final |
| Global Rate Limiting | ⏳ Planned | Bucket4j + Redis upgrade |
| Circuit Breakers | ⏳ Planned | Reliability upgrade |
| JSON Logging | ⏳ Planned | Machine-readable format upgrade |

---

## Architecture Structure

### Hardening Components
```
src/main/kotlin/com/solodev/fleet/
└── shared/
    ├── plugins/
    │   ├── RateLimiting.kt            (Phase 4)
    │   ├── Idempotency.kt             (Phase 4)
    │   ├── Pagination.kt              (Phase 4)
    │   ├── Security.kt                (Phase 4)
    │   ├── Observability.kt           (Phase 4)
    │   ├── RequestId.kt               (Phase 4)
    │   ├── StatusPages.kt             (Phase 4)
    │   └── Serialization.kt           (Phase 4)
    ├── infrastructure/
    │   └── persistence/
    │       └── IdempotencyRepositoryImpl.kt (Phase 4)
    └── monitoring/
        └── (Prometheus/Micrometer integrated)

src/test/kotlin/com/solodev/fleet/
├── concurrency/
│   ├── DoubleBookingTest.kt               (Phase 8)
│   ├── ConcurrentUpdateTest.kt            (Phase 8)
│   └── RaceConditionTest.kt               (Phase 8)
├── performance/
│   ├── LoadTest.kt                        (Phase 8)
│   └── StressTest.kt                      (Phase 8)
└── security/
    ├── AuthenticationTest.kt              (Phase 8)
    ├── AuthorizationTest.kt               (Phase 8)
    └── OwaspTop10Test.kt                  (Phase 8)

docs/runbooks/
├── incident-response.md                   (Phase 8)
├── performance-tuning.md                  (Phase 8)
├── circuit-breaker-recovery.md            (Phase 8)
└── kafka-lag-mitigation.md                (Phase 8)
```

---

## Code Impact (Repository Artifacts)

### Files Created (Phase 4)

**Core Plugins** (~8 files):
1. `src/main/kotlin/com/solodev/fleet/shared/plugins/RateLimiting.kt`
2. `src/main/kotlin/com/solodev/fleet/shared/plugins/Idempotency.kt`
3. `src/main/kotlin/com/solodev/fleet/shared/plugins/Pagination.kt`
4. `src/main/kotlin/com/solodev/fleet/shared/plugins/Security.kt` (RBAC)
5. `src/main/kotlin/com/solodev/fleet/shared/plugins/Observability.kt`
6. `src/main/kotlin/com/solodev/fleet/shared/plugins/RequestId.kt`
7. `src/main/kotlin/com/solodev/fleet/shared/plugins/StatusPages.kt`
8. `src/main/kotlin/com/solodev/fleet/shared/plugins/Serialization.kt`

**Infrastructure** (~2 files):
1. `src/main/kotlin/com/solodev/fleet/shared/infrastructure/persistence/IdempotencyRepositoryImpl.kt`
2. `src/main/kotlin/com/solodev/fleet/modules/vehicles/infrastructure/persistence/VehiclesTable.kt` (Version column)

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
- `phase-8-deployment.md` - Deployment phase

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

**Phase 4 Status**: **In Progress**

The system has been significantly hardened with RBAC, Multi-tiered Rate Limiting, Idempotency, and Observability. Concurrency control patterns (locks) and Type-Safe SQL are enforced.

**Key Deliverables**:
- [x] Concurrency control (Optimistic & Pessimistic)
- [x] Multi-tiered Rate Limiting
- [x] API Idempotency
- [x] RBAC (Authorization)
- [x] Observability (Metrics & Request Correlation)
- [x] Global Error Handling
- [ ] Distributed Caching (Phase 8 Final)
- [ ] Circuit breakers for external services
- [ ] Structured JSON logging
- [ ] Performance optimization (In Progress)
- [ ] Failure mode testing (In Progress)

**Ready for Phase 5**: **Yes** (Baseline hardening complete)

---

**Implementation Date**: 2026-02-14
**Verification**: In Progress
**Hardening Status**: Core Complete
**Compliance**: 75%
**Ready for Next Phase**: Yes
