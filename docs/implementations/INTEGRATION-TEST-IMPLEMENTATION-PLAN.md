# Integration Test Implementation Plan

**Version**: 1.2  
**Date**: 2026-03-08  
**Status**: Phase A ✅ Unit Test Compliance (2026-03-08); Phase B ✅ Missing Unit Tests (2026-03-08); Phases C–E in planning  
**Practices Reference**: [PRACTICES_INTEGRATION_TEST.md](./PRACTICES_INTEGRATION_TEST.md)  
**Unit Test Compliance Reference**: [PRACTICES_UNIT_TEST.md](./PRACTICES_UNIT_TEST.md)

---

## Table of Contents

1. [Current Test Coverage Summary](#1-current-test-coverage-summary)
2. [Unit Test Compliance Audit](#2-unit-test-compliance-audit)
3. [Integration Test Infrastructure Setup](#3-integration-test-infrastructure-setup)
4. [Integration Test Plan — Module by Module](#4-integration-test-plan--module-by-module)
   - [4.1 Users Module](#41-users-module)
   - [4.2 Vehicles Module](#42-vehicles-module)
   - [4.3 Rentals Module](#43-rentals-module)
   - [4.4 Customers Module](#44-customers-module)
   - [4.5 Maintenance Module](#45-maintenance-module)
   - [4.6 Accounting Module](#46-accounting-module)
   - [4.7 Tracking Module](#47-tracking-module)
5. [Priority and Sequencing](#5-priority-and-sequencing)

---

## 1. Current Test Coverage Summary

### Current Tests (59 files)

| Module | Domain Tests | Use Case Tests | Integration Tests | Total |
|--------|-------------|----------------|-------------------|-------|
| **Users** | 2 | 7 | ❌ None | 9 |
| **Vehicles** | 1 | 7 | ❌ None | 8 |
| **Rentals** | 2 | 7 | ❌ None | 9 |
| **Customers** | 1 | 2 | ❌ None | 3 |
| **Maintenance** | 2 | 5 | ❌ None | 7 |
| **Accounts** | 2 | 5 | ❌ None | 7 |
| **Tracking** | 5 (DTOs) | 2 | ❌ None | 7 |
| **Shared/App** | 1 (Location) | — | ✅ ApplicationTest (H2, health/root only) | 2 |
| **Spatial** | — | — | ⚠️ PostGISAdapterTest (disabled) | 1 |
| **Migration** | — | — | ⚠️ MigrationTest (H2 only) | 1 |
| **TOTAL** | **16** | **33** | **2 partial** | **54** |

### Critical Gap

There are **zero HTTP-level integration tests** for any API module. The only integration test is `ApplicationTest.kt`, which only verifies the `/health` and `/` endpoints with H2 in-memory DB. This means no route-to-database flow has been tested end-to-end.

---

## 2. Unit Test Compliance Audit

> **✅ Phase A Complete (2026-03-08)** — All 22 use case tests are now fully compliant. AssertJ 3.27.3 integrated, `shouldX_WhenY` method naming applied, `// Arrange / // Act / // Assert` comments added, and generic `any()` replaced with exact typed values or `slot<T>()` captures. Build verified: `BUILD SUCCESSFUL`. See [Phase A in §5](#5-priority-and-sequencing).

This section audits existing unit tests against [PRACTICES_UNIT_TEST.md](./PRACTICES_UNIT_TEST.md).

### 2.1 Compliance Summary

| Practice Rule | Requirement | Compliance | Verdict |
|---------------|-------------|------------|---------|
| **Framework** | JUnit 5 + MockK | JUnit 5 ✅, MockK ✅ | ✅ Pass |
| **Isolation** | No Ktor, no DB, no testcontainers | Mostly ✅ | ✅ Pass |
| **Assertions** | AssertJ exclusively (`assertThat`) | AssertJ 3.27.3 — all 22 use case tests ✅ | ✅ **Fixed** (2026-03-08) |
| **Naming** | `should[Behavior]_When[Condition]` | Applied to all 22 use case tests ✅ | ✅ **Fixed** (2026-03-08) |
| **AAA Comments** | `// Arrange`, `// Act`, `// Assert` in every test | Added to all 22 use case tests ✅ | ✅ **Fixed** (2026-03-08) |
| **Matchers** | No generic `any()` unless unavoidable | Exact typed values / `slot<T>()` captures ✅ | ✅ **Fixed** (2026-03-08) |
| **Mock scope** | `@MockK` + `@InjectMockKs` | Manual `mockk<>()` + manual constructor | ⚠️ Partial |
| **Happy + Edge cases** | Both required | Happy paths present; some edge cases | ⚠️ Partial |
| **No IO / No frameworks** | Pure JVM | `CreateRentalUseCaseTest` bypasses use case calling ❌ | ⚠️ Note |

---

### 2.2 Detailed Findings per Module

> **✅ All compliance issues listed below were resolved on 2026-03-08.** The tables below are preserved as historical record of the pre-compliance state. Each module's test files have been rewritten with AssertJ, `shouldX_WhenY` naming, and AAA comments.

#### USERS MODULE

| Test File | Issues Found | Status |
|-----------|--------------|--------|
| `RegisterUserUseCaseTest` | Used `kotlin.test.assertEquals`; no AAA comments; generic `save(any())`; method name was natural language | ✅ Fixed (2026-03-08) |
| `LoginUserUseCaseTest` | Same assertion framework issues; no AAA comments; missing negative test for wrong password | ✅ Fixed (2026-03-08) |
| `AssignRoleUseCaseTest` | Same issues; missing test for non-existent user | ✅ Fixed (2026-03-08) |
| `VerifyEmailUseCaseTest` | Same issues; missing expired token edge case | ✅ Fixed (2026-03-08) |
| `UserTest` / `VerificationTokenTest` | Domain tests: acceptable natural language — low severity | ✅ Acceptable |

**Accounts module was most lacking**: `ManageAccountUseCaseTest`, `GenerateFinancialReportsUseCaseTest`, `ReconciliationServiceTest` were missing — all added in Phase B (2026-03-08).

#### VEHICLES MODULE

| Test File | Issues Found | Status |
|-----------|--------------|--------|
| `CreateVehicleUseCaseTest` | No AAA comments; generic `any()` in `coVerify`; AssertJ not used | ✅ Fixed (2026-03-08) |
| `GetVehicleUseCaseTest` | Same issues; missing vehicle-not-found test | ✅ Fixed (2026-03-08) |
| `DeleteVehicleUseCaseTest` | Same issues; missing delete-RENTED-vehicle test | ✅ Fixed (2026-03-08) |
| `ListVehiclesUseCaseTest` | Same issues | ✅ Fixed (2026-03-08) |
| **Missing Use Cases** | `UpdateVehicleUseCase`, `UpdateVehicleStateUseCase`, `RecordOdometerUseCase` were untested | ✅ Added in Phase B (2026-03-08) |

#### RENTALS MODULE

| Test File | Issues Found | Status |
|-----------|--------------|--------|
| `CreateRentalUseCaseTest` | `useCase.execute()` not called — tests domain logic directly (by design: wraps `dbQuery{}`) | ⚠️ By Design |
| `ActivateRentalUseCaseTest` | Same assertion/AAA issues | ✅ Fixed (2026-03-08) |
| `CompleteRentalUseCaseTest` | Same issues | ✅ Fixed (2026-03-08) |
| `CancelRentalUseCaseTest` | Same issues | ✅ Fixed (2026-03-08) |
| **Missing** | `GetRentalUseCaseTest` was missing | ✅ Added in Phase B (2026-03-08) |

#### MAINTENANCE MODULE

| Test File | Issues Found | Status |
|-----------|--------------|--------|
| `CompleteMaintenanceUseCaseTest` | Generic `any()` used; no AAA comments; AssertJ not used | ✅ Fixed (2026-03-08) |
| `ScheduleMaintenanceUseCaseTest` | Same issues | ✅ Fixed (2026-03-08) |
| `CancelMaintenanceUseCaseTest` | Same issues | ✅ Fixed (2026-03-08) |
| **Missing** | `ListVehicleMaintenanceUseCase` was not covered | ✅ Added in Phase B (2026-03-08) |

#### ACCOUNTS MODULE

| Test File | Issues Found | Status |
|-----------|--------------|--------|
| `IssueInvoiceUseCaseTest` | Generic `any()` in `coEvery { invoiceRepo.save(any()) }`; no AAA comments; AssertJ not used | ✅ Fixed (2026-03-08) |
| `PayInvoiceUseCaseTest` | Same issues | ✅ Fixed (2026-03-08) |
| **Missing — high priority** | `ManageAccountUseCaseTest`, `GenerateFinancialReportsUseCaseTest`, `ReconciliationServiceTest` were absent | ✅ Added in Phase B (2026-03-08) |

#### TRACKING MODULE

| Test File | Issues Found | Status |
|-----------|--------------|--------|
| `UpdateVehicleLocationUseCaseTest` | Hand-rolled `MockDeltaBroadcaster`; AssertJ not used; no AAA comments; tests mock class not actual use case | ✅ Fixed (2026-03-08) |
| `CircuitBreakerTest` | Good coverage of resilience — acceptable | ✅ Acceptable |
| `RetryPolicyTest` | Good coverage of resilience — acceptable | ✅ Acceptable |

---

### 2.3 Patterns Fixed ✅ (Reference)

> All four patterns below were applied to all 22 use case tests on 2026-03-08.

**1 — Assertion framework replaced (kotlin.test → AssertJ):**
```kotlin
// BEFORE
assertEquals("juan@fleet.ph", result.email)
assertFailsWith<IllegalStateException> { ... }

// AFTER ✅
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

assertThat(result.email).isEqualTo("juan@fleet.ph")
assertThatThrownBy { runBlocking { useCase.execute(request) } }
    .isInstanceOf(IllegalStateException::class.java)
```

**2 — AAA comments added to all test methods:**
```kotlin
@Test
fun shouldRegisterUser_WhenEmailIsNew() = runBlocking {
    // Arrange
    coEvery { userRepository.findByEmail("juan@fleet.ph") } returns null
    coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
    coEvery { userRepository.save(any()) } returnsArgument 0
    coEvery { tokenRepository.save(any()) } returnsArgument 0

    // Act
    val result = useCase.execute(validRequest)

    // Assert
    assertThat(result.email).isEqualTo("juan@fleet.ph")
    assertThat(result.isVerified).isFalse()
}
```

**3 — Generic `any()` replaced with exact values or `slot<T>()` captures:**
```kotlin
// BEFORE
coEvery { repository.save(any()) } returnsArgument 0
coVerify { repository.save(any()) }

// AFTER ✅
val vehicleSlot = slot<Vehicle>()
coEvery { repository.save(capture(vehicleSlot)) } returnsArgument 0
// ...
assertThat(vehicleSlot.captured.licensePlate).isEqualTo("ABC-1234")
```

**4 — Test methods renamed to `shouldX_WhenY` pattern:**
```kotlin
// BEFORE
fun `creates vehicle with valid data`()
fun `throws when email is already registered`()

// AFTER ✅
fun shouldCreateVehicle_WhenDataIsValid()
fun shouldThrowIllegalState_WhenEmailAlreadyRegistered()
```

---

### 2.4 Missing Unit Tests ✅ All Done (2026-03-08)

| Module | Test Created | Priority | Status |
|--------|-------------|----------|--------|
| Vehicles | `UpdateVehicleUseCaseTest` | HIGH | ✅ Done |
| Vehicles | `UpdateVehicleStateUseCaseTest` | HIGH | ✅ Done |
| Vehicles | `RecordOdometerUseCaseTest` | HIGH | ✅ Done |
| Accounts | `ManageAccountUseCaseTest` | HIGH | ✅ Done |
| Accounts | `GenerateFinancialReportsUseCaseTest` | HIGH | ✅ Done |
| Accounts | `ReconciliationServiceTest` | MEDIUM | ✅ Done |
| Users | `GetUserProfileUseCaseTest` | MEDIUM | ✅ Done |
| Users | `UpdateUserUseCaseTest` | MEDIUM | ✅ Done |
| Users | `ListUsersUseCaseTest` | LOW | ✅ Done |
| Rentals | `GetRentalUseCaseTest` | MEDIUM | ✅ Done |
| Maintenance | `ListVehicleMaintenanceUseCaseTest` | LOW | ✅ Done |

---

## 3. Integration Test Infrastructure Setup

All integration tests must follow [PRACTICES_INTEGRATION_TEST.md](./PRACTICES_INTEGRATION_TEST.md).

### 3.1 Shared Test Infrastructure

**File**: `src/test/kotlin/com/solodev/fleet/IntegrationTestBase.kt`

```kotlin
abstract class IntegrationTestBase {
    companion object {
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgis/postgis:15-3.3-alpine").apply {
            withDatabaseName("fleet_test")
            withUsername("fleet_user")
            withPassword("test_password")
            start()
        }

        @JvmStatic
        fun buildTestConfig(): MapApplicationConfig = MapApplicationConfig(
            "storage.jdbcUrl"          to postgres.jdbcUrl,
            "storage.username"         to postgres.username,
            "storage.password"         to postgres.password,
            "storage.driverClassName"  to "org.postgresql.Driver",
            "storage.maximumPoolSize"  to "2",
            "jwt.secret"               to "test-secret-at-least-64-bytes-long-for-hmac-sha256-testing",
            "jwt.issuer"               to "test-issuer",
            "jwt.audience"             to "test-audience",
            "jwt.realm"                to "test-realm",
            "jwt.expiresIn"            to "3600000",
            "redis.enabled"            to "false"
        )
    }

    // Utility: truncate tables before each test
    fun cleanDatabase() {
        transaction {
            exec("TRUNCATE TABLE rentals, customers, vehicles, users, ...")
        }
    }
}
```

**Key design decisions:**
- Single `PostgreSQLContainer` instance (Singleton pattern) — starts once per JVM, shared by all suites
- Flyway migrations run automatically on application startup
- Redis disabled in test configs (use `redis.enabled = false`)
- All auth-required tests obtain a JWT from `POST /v1/users/login` first
- `@BeforeEach` truncates relevant tables for isolation

---

### 3.2 Test Client Utilities

```kotlin
// Helper to obtain JWT token for test requests
suspend fun ApplicationTestBuilder.getAdminToken(): String {
    // Register + login an admin user, return the JWT
}

// AssertJ-based HTTP response assertion helpers
fun assertOk(response: HttpResponse) =
    assertThat(response.status.value).isEqualTo(200)
```

---

## 4. Integration Test Plan — Module by Module

### 4.1 Users Module

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

### 4.2 Vehicles Module

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

### 4.3 Rentals Module

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

### 4.4 Customers Module

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

### 4.5 Maintenance Module

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

### 4.6 Accounting Module

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

### 4.7 Tracking Module

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

## 5. Priority and Sequencing

### Phase A — Fix Unit Tests ✅ Complete (2026-03-08)

1. ✅ Migrate all assertions from `kotlin.test` → AssertJ (`assertThat`)
2. ✅ Add `// Arrange`, `// Act`, `// Assert` comments to all test methods
3. ✅ Rename test methods to `should[Behavior]_When[Condition]` pattern
4. ✅ Replace generic `any()` with exact typed values or `slot<T>()` captures
5. ✅ Replace hand-rolled `MockDeltaBroadcaster` with MockK in tracking tests

### Phase B — Add Missing Unit Tests ✅ Complete (2026-03-08)

1. ✅ `UpdateVehicleUseCaseTest`
2. ✅ `UpdateVehicleStateUseCaseTest`
3. ✅ `RecordOdometerUseCaseTest`
4. ✅ `ManageAccountUseCaseTest`
5. ✅ `GenerateFinancialReportsUseCaseTest`
6. ✅ `ReconciliationServiceTest`
7. ✅ `GetUserProfileUseCaseTest`
8. ✅ `UpdateUserUseCaseTest`
9. ✅ `ListUsersUseCaseTest`
10. ✅ `GetRentalUseCaseTest`
11. ✅ `ListVehicleMaintenanceUseCaseTest`

### Phase C — Integration Test Infrastructure (1 day)

1. Add `testcontainers-postgresql` dependency (already in `build.gradle.kts`)
2. Create `IntegrationTestBase.kt` with shared container
3. Add test utilities (JWT helper, table cleanup, HTTP client config)

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
testImplementation("io.mockk:mockk")

// ADD if missing:
testImplementation("org.assertj:assertj-core:3.27.3") // ✅ Added
testImplementation("org.testcontainers:testcontainers:1.19.x")
```

---

*Last Updated: 2026-03-08 (v1.2 — Phase B complete; 11 missing use case tests added; total use case tests 22 → 33)*
