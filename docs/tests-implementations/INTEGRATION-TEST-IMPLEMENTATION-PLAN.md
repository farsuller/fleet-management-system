# Integration Test Implementation Plan

**Version**: 1.3  
**Date**: 2026-03-08  
**Status**: Phase C ✅ Integration Test Infrastructure (2026-03-08); Phases D–E in planning  
**Practices Reference**: [PRACTICES_INTEGRATION_TEST.md](./PRACTICES_INTEGRATION_TEST.md)  
**Unit Test Plan**: [UNIT-TEST-IMPLEMENTATION-PLAN.md](./UNIT-TEST-IMPLEMENTATION-PLAN.md)

---

## Table of Contents

1. [Current Integration Test Coverage Summary](#1-current-integration-test-coverage-summary)
2. [Integration Test Infrastructure Setup](#2-integration-test-infrastructure-setup)
   - [2.1 Shared Test Infrastructure](#21-shared-test-infrastructure)
   - [2.2 Test Client Utilities](#22-test-client-utilities)
3. [Integration Test Plan — Module by Module](#3-integration-test-plan--module-by-module)
   - [3.1 Users Module](#31-users-module)
   - [3.2 Vehicles Module](#32-vehicles-module)
   - [3.3 Rentals Module](#33-rentals-module)
   - [3.4 Customers Module](#34-customers-module)
   - [3.5 Maintenance Module](#35-maintenance-module)
   - [3.6 Accounting Module](#36-accounting-module)
   - [3.7 Tracking Module](#37-tracking-module)
4. [Priority and Sequencing](#4-priority-and-sequencing)

---

## 1. Current Integration Test Coverage Summary

| Module | Integration Tests | Status |
|--------|-------------------|--------|
| **Users** | ❌ None | Planned |
| **Vehicles** | ❌ None | Planned |
| **Rentals** | ❌ None | Planned |
| **Customers** | ❌ None | Planned |
| **Maintenance** | ❌ None | Planned |
| **Accounts** | ❌ None | Planned |
| **Tracking** | ❌ None | Planned |
| **Shared/App** | ✅ ApplicationTest (H2, health/root only) | Partial |
| **Spatial** | ⚠️ PostGISAdapterTest (disabled) | Disabled |
| **Migration** | ⚠️ MigrationTest (H2 only) | Partial |

### Critical Gap

There are **zero HTTP-level integration tests** for any API module. The only integration test is `ApplicationTest.kt`, which only verifies the `/health` and `/` endpoints with H2 in-memory DB. This means no route-to-database flow has been tested end-to-end.

---

## 2. Integration Test Infrastructure Setup

All integration tests must follow [PRACTICES_INTEGRATION_TEST.md](./PRACTICES_INTEGRATION_TEST.md).

### 2.1 Shared Test Infrastructure ✅ Complete (2026-03-08)

**File**: [src/test/kotlin/com/solodev/fleet/IntegrationTestBase.kt](../../src/test/kotlin/com/solodev/fleet/IntegrationTestBase.kt)

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class IntegrationTestBase {

    companion object {
        const val JWT_SECRET = "test-secret-at-least-64-bytes-long-for-hmac-sha256-security-1234567890"
        const val JWT_ISSUER = "test-issuer"
        const val JWT_AUDIENCE = "test-audience"

        val postgres: PostgreSQLContainer<Nothing> by lazy {
            PostgreSQLContainer<Nothing>(
                DockerImageName.parse("postgis/postgis:15-3.3-alpine")
                    .asCompatibleSubstituteFor("postgres")
            ).apply {
                withDatabaseName("fleet_test")
                withUsername("fleet_user")
                withPassword("test_password")
                start()
            }
        }

        fun buildTestConfig(): MapApplicationConfig = MapApplicationConfig(
            "storage.jdbcUrl"         to postgres.jdbcUrl,
            "storage.username"        to postgres.username,
            "storage.password"        to postgres.password,
            "storage.driverClassName" to "org.postgresql.Driver",
            "storage.maximumPoolSize" to "2",
            "jwt.secret"              to JWT_SECRET,
            "jwt.issuer"              to JWT_ISSUER,
            "jwt.audience"            to JWT_AUDIENCE,
            "jwt.realm"               to "test-realm",
            "jwt.expiresIn"           to "3600000",
            "redis.enabled"           to "false"
        )

        /** Generates a signed JWT directly — no DB user registration required. */
        fun tokenFor(id: String, email: String, vararg roles: String): String =
            JwtService(JWT_SECRET, JWT_ISSUER, JWT_AUDIENCE, 3_600_000L)
                .generateToken(id, email, roles.toList())
    }

    @BeforeAll
    fun assumeDockerIsAvailable() {
        assumeTrue(isDockerAvailable(), "Skipping — Docker not reachable.")
    }

    /** Truncates all transactional tables; preserves seed data (roles, accounts, payment_methods, routes, geofences). */
    fun cleanDatabase() {
        postgres.createConnection("").use { conn ->
            conn.createStatement().execute(
                "TRUNCATE TABLE location_history, idempotency_keys, dlq_messages, " +
                "inbox_processed_messages, outbox_events, payments, invoice_line_items, " +
                "invoices, ledger_entry_lines, ledger_entries, maintenance_schedules, " +
                "maintenance_parts, maintenance_jobs, rental_payments, rental_charges, " +
                "rental_periods, rentals, customers, odometer_readings, vehicles, " +
                "verification_tokens, staff_profiles, user_roles, users CASCADE"
            )
        }
    }
}

/** Top-level extension — use inside testApplication { } blocks. */
fun ApplicationTestBuilder.configurePostgres() {
    environment { config = IntegrationTestBase.buildTestConfig() }
}
```

**Key design decisions:**
- `by lazy` singleton — container starts once per JVM, reused by all suites
- Flyway migrations run on first `module()` startup; subsequent starts skip (idempotent)
- Redis disabled (`redis.enabled = false`) — no Redis required in test environment
- JWT generated directly via `JwtService` — no registered DB user needed for auth-required tests
- `cleanDatabase()` preserves seed/reference tables: `roles`, `accounts` (chart of accounts), `payment_methods`, `routes`, `geofences`
- Docker guard via `assumeTrue(isDockerAvailable())` — test class skipped if Docker not reachable
- `TESTCONTAINERS_RYUK_DISABLED=true` and `DOCKER_HOST=tcp://localhost:2375` already set in `build.gradle.kts tasks.test`

---

### 2.2 Test Client Utilities ✅ Complete (2026-03-08)

All utilities are in `IntegrationTestBase.kt`:

```kotlin
// Generate token for any role — no HTTP roundtrip
val adminToken = tokenFor("uuid-here", "admin@fleet.ph", "ADMIN")
val managerToken = tokenFor("uuid-here", "mgr@fleet.ph", "FLEET_MANAGER")
val customerToken = tokenFor("uuid-here", "cust@fleet.ph", "CUSTOMER")

// Wire app to container inside testApplication { }
testApplication {
    configurePostgres()
    application { module() }
    client.get("/v1/vehicles") {
        bearerAuth(adminToken)
    }
}
```

---

## 3. Integration Test Plan — Module by Module

### 3.1 Users Module

**File**: `src/test/kotlin/com/solodev/fleet/modules/users/UsersIntegrationTest.kt`

| Test Method | Scenario | Expected |
|-------------|----------|----------|
| `shouldRegisterUser_WhenRequestIsValid` | POST /v1/users/register with valid data | 201, user in DB |
| `shouldReturn409_WhenEmailAlreadyExists` | POST /v1/users/register with duplicate email | 409 |
| `shouldReturn400_WhenEmailIsMissing` | POST /v1/users/register, missing email | 400 |
| `shouldLoginUser_WhenCredentialsAreValid` | POST /v1/users/login | 200, JWT in response |
| `shouldReturn401_WhenPasswordIsWrong` | POST /v1/users/login with bad password | 401 |
| `shouldReturn401_WhenUserNotVerified` | Login before email verification | 401 |
| `shouldVerifyEmail_WhenTokenIsValid` | GET /v1/users/verify?token=... | 200 |
| `shouldReturn400_WhenTokenIsExpired` | GET /v1/users/verify with expired token | 400 |
| `shouldGetProfile_WhenTokenIsValid` | GET /v1/users/profile (Bearer) | 200, profile |
| `shouldReturn401_WhenNoAuthToken` | GET /v1/users/profile no auth | 401 |
| `shouldAssignRole_WhenAdminRequests` | POST /v1/users/{id}/roles (ADMIN only) | 200 |
| `shouldReturn403_WhenNonAdminAssignsRole` | Same endpoint, non-ADMIN user | 403 |

**DB assertions**: After registration, verify `users` table contains the record. After login, verify no `is_verified` bypass.

---

### 3.2 Vehicles Module

**File**: `src/test/kotlin/com/solodev/fleet/modules/vehicles/VehiclesIntegrationTest.kt`

| Test Method | Scenario | Expected |
|-------------|----------|----------|
| `shouldCreateVehicle_WhenAdminRequests` | POST /v1/vehicles | 201, vehicle in DB |
| `shouldReturn403_WhenCustomerCreatesVehicle` | POST /v1/vehicles as CUSTOMER role | 403 |
| `shouldReturn400_WhenVinIsInvalid` | POST /v1/vehicles with VIN != 17 chars | 400 |
| `shouldReturn409_WhenLicensePlateExists` | POST /v1/vehicles duplicate plate | 409 |
| `shouldGetVehicle_WhenIdExists` | GET /v1/vehicles/{id} | 200 |
| `shouldReturn404_WhenVehicleNotFound` | GET /v1/vehicles/{unknown-id} | 404 |
| `shouldListVehicles_WithPagination` | GET /v1/vehicles?page=0&size=10 | 200, array |
| `shouldUpdateVehicle_WhenFleetManagerRequests` | PUT /v1/vehicles/{id} | 200 |
| `shouldChangeVehicleState_ToRented` | POST /v1/vehicles/{id}/state (RENTED) | 200, state updated |
| `shouldReturn409_WhenStateTransitionIsInvalid` | DECOMMISSIONED → AVAILABLE | 409 |
| `shouldRecordOdometer_WhenReadingIsHigher` | POST /v1/vehicles/{id}/odometer | 201 |
| `shouldReturn400_WhenOdometerDecrease` | POST odometer with lower reading | 400 |
| `shouldDeleteVehicle_WhenAdmin` | DELETE /v1/vehicles/{id} | 204 |

---

### 3.3 Rentals Module

**File**: `src/test/kotlin/com/solodev/fleet/modules/rentals/RentalsIntegrationTest.kt`

| Test Method | Scenario | Expected |
|-------------|----------|----------|
| `shouldCreateRental_WhenVehicleIsAvailable` | POST /v1/rentals | 201, rental RESERVED in DB |
| `shouldReturn409_WhenVehicleAlreadyRented` | POST /v1/rentals, overlapping dates | 409 |
| `shouldReturn400_WhenEndDateBeforeStartDate` | POST /v1/rentals bad dates | 400 |
| `shouldReturn404_WhenVehicleDoesNotExist` | POST /v1/rentals unknown vehicleId | 404 |
| `shouldActivateRental_WhenStatusIsReserved` | POST /v1/rentals/{id}/activate | 200, ACTIVE |
| `shouldReturn409_WhenActivatingCompletedRental` | Activate already COMPLETED | 409 |
| `shouldCompleteRental_WhenStatusIsActive` | POST /v1/rentals/{id}/complete | 200, COMPLETED |
| `shouldCancelRental_WhenStatusIsReserved` | POST /v1/rentals/{id}/cancel | 200, CANCELLED |
| `shouldGetRental_WhenIdExists` | GET /v1/rentals/{id} | 200 |
| `shouldListRentals_WithPagination` | GET /v1/rentals | 200, paginated |
| `shouldPreventDoubleBooking_AtDatabaseLevel` | Concurrent POST requests same vehicle+dates | Only 1 succeeds (exclusion constraint) |

**Double-booking test** is the most critical — must use concurrent coroutines to hit the PostGIS exclusion constraint at DB level.

---

### 3.4 Customers Module

**File**: `src/test/kotlin/com/solodev/fleet/modules/rentals/CustomersIntegrationTest.kt`

| Test Method | Scenario | Expected |
|-------------|----------|----------|
| `shouldCreateCustomer_WhenDataIsValid` | POST /v1/customers | 201, in DB |
| `shouldReturn409_WhenEmailAlreadyExists` | POST /v1/customers duplicate email | 409 |
| `shouldReturn400_WhenLicenseExpiryIsPast` | POST /v1/customers, expired license | 400 |
| `shouldReturn400_WhenDriverLicenseIsMissing` | POST /v1/customers no license | 400 |
| `shouldGetCustomer_WhenIdExists` | GET /v1/customers/{id} | 200 |
| `shouldReturn404_WhenCustomerNotFound` | GET /v1/customers/{unknown} | 404 |
| `shouldListCustomers_WithFilter` | GET /v1/customers?isActive=true | 200, filtered |

---

### 3.5 Maintenance Module

**File**: `src/test/kotlin/com/solodev/fleet/modules/maintenance/MaintenanceIntegrationTest.kt`

| Test Method | Scenario | Expected |
|-------------|----------|----------|
| `shouldScheduleMaintenance_WhenVehicleExists` | POST /v1/maintenance | 201, SCHEDULED in DB |
| `shouldReturn404_WhenVehicleNotFound` | POST /v1/maintenance, unknown vehicleId | 404 |
| `shouldStartMaintenance_WhenJobIsScheduled` | POST /v1/maintenance/{id}/start | 200, IN_PROGRESS |
| `shouldReturn409_WhenStartingCompletedJob` | Start already COMPLETED job | 409 |
| `shouldCompleteMaintenance_WithCosts` | POST /v1/maintenance/{id}/complete | 200, COMPLETED, costs saved |
| `shouldReturn400_WhenCompletingNonStartedJob` | Complete SCHEDULED job directly | 400 |
| `shouldCancelMaintenance_WhenJobIsScheduled` | POST /v1/maintenance/{id}/cancel | 200, CANCELLED |
| `shouldListMaintenanceByVehicle` | GET /v1/maintenance/vehicle/{id} | 200, list |
| `shouldReturn403_WhenCustomerAccessesMaintenance` | GET... as CUSTOMER role | 403 |

---

### 3.6 Accounting Module

**File**: `src/test/kotlin/com/solodev/fleet/modules/accounts/AccountingIntegrationTest.kt`

| Test Method | Scenario | Expected |
|-------------|----------|----------|
| `shouldCreateAccount_WhenAdminRequests` | POST /v1/accounting/accounts | 201, account in DB |
| `shouldReturn409_WhenAccountCodeAlreadyExists` | Duplicate account code | 409 |
| `shouldListAccounts_WhenRequested` | GET /v1/accounting/accounts | 200, list |
| `shouldIssueInvoice_AndPostLedgerEntries` | POST /v1/accounting/invoices | 201, DR AR + CR Revenue |
| `shouldReturn400_WhenARAccountMissing` | POST invoice without account code 1100 in DB | 400 |
| `shouldPayInvoice_WhenInvoiceExists` | POST /v1/accounting/invoices/{id}/pay | 200, status PAID |
| `shouldReturn409_WhenPayingAlreadyPaidInvoice` | Pay PAID invoice twice | 409 |
| `shouldBeIdempotent_WhenPayingWithSameKey` | POST pay with `Idempotency-Key` used twice | 200 (same result) |
| `shouldGetLedger_WithDateRange` | GET /v1/accounting/ledger?from=...&to=... | 200 |
| `shouldGenerateRevenueReport` | GET /v1/accounting/reports/revenue | 200, valid totals |
| `shouldGenerateBalanceSheet` | GET /v1/accounting/reports/balance-sheet | 200, debits == credits |
| `shouldVerifyDoubleEntryIntegrity_WhenLedgerPosted` | Check sum(debit) == sum(credit) after invoice | DB assertion |

---

### 3.7 Tracking Module

**File**: `src/test/kotlin/com/solodev/fleet/modules/tracking/TrackingIntegrationTest.kt`

| Test Method | Scenario | Expected |
|-------------|----------|----------|
| `shouldAcceptLocationUpdate_WhenVehicleExists` | POST /v1/tracking/vehicles/{id}/location | 200 |
| `shouldReturn404_WhenVehicleNotFound` | POST location update unknown vehicleId | 404 |
| `shouldReturn429_WhenRateLimitExceeded` | Rapid successive POST requests | 429 |
| `shouldPersistLocationHistory_AfterUpdate` | POST location → verify in DB | 201, row in `location_history` |
| `shouldUpgradeWebSocket_WhenValidRequest` | WS /v1/tracking/ws/{vehicleId} | 101 Switching Protocols |
| `shouldReceiveDelta_WhenVehiclePositionChanges` | WS subscribe → send location → receive delta | Delta message |

**WebSocket tests** require Ktor's `WebSocketClient` within `testApplication`.

---

## 4. Priority and Sequencing

### Phase C — Integration Test Infrastructure ✅ Complete (2026-03-08)

1. ✅ `testcontainers-postgresql` dependency — already present (v1.20.4)
2. ✅ Created `IntegrationTestBase.kt` with singleton PostGIS container
3. ✅ `tokenFor()` JWT helper, `cleanDatabase()` TRUNCATE utility, `configurePostgres()` extension

### Phase D — Core Integration Tests (3–5 days)

Priority order:
1. **Rentals** — highest risk (double-booking constraint, state machine)
2. **Accounting** — financial integrity (double-entry, idempotency)
3. **Users** — authentication flow (register → verify → login)
4. **Vehicles** — state transitions, odometer validation
5. **Maintenance** — lifecycle transitions
6. **Customers** — basic CRUD validation
7. **Tracking** — location persistence + WebSocket

### Phase E — Tracking WebSocket Integration Tests (1–2 days)

WebSocket tests are isolated last due to their async nature and dependency on Redis broadcaster or in-memory fallback.

---

## Appendix: Dependencies Required

Verify these are present in `build.gradle.kts`:

```kotlin
// Already present:
testImplementation("org.testcontainers:postgresql:1.19.x")
testImplementation("io.ktor:ktor-server-test-host")
testImplementation("org.testcontainers:testcontainers:1.19.x")
```

---

*Last Updated: 2026-03-08 (v1.3 — Phase C complete; IntegrationTestBase.kt created with PostGIS container, JWT helper, cleanDatabase())*
