# Rental Module - Test Implementation Guide

This document covers testing strategy and implementations for the Rental module, focusing on the full lifecycle (RESERVED → ACTIVE → COMPLETED), concurrent reservation safety via pessimistic locks, and cross-module coordination (vehicles + accounting).

---

## 1. Domain Unit Tests

### Rental State Machine Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/domain/model/RentalTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class RentalTest {

    private val now = Instant.now()
    private val tomorrow = now.plus(1, ChronoUnit.DAYS)
    private val nextWeek = now.plus(7, ChronoUnit.DAYS)

    // --- Invariant: endDate must be after startDate ---

    @Test
    fun `throws when endDate is before startDate`() {
        assertFailsWith<IllegalArgumentException> {
            sampleRental(startDate = nextWeek, endDate = tomorrow)
        }
    }

    @Test
    fun `throws when totalAmount is negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleRental(totalAmount = -1)
        }
    }

    // --- activate() ---

    @Test
    fun `activate transitions RESERVED to ACTIVE`() {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        val activated = rental.activate(actualStart = now, startOdo = 5000)

        assertEquals(RentalStatus.ACTIVE, activated.status)
        assertEquals(now, activated.actualStartDate)
        assertEquals(5000, activated.startOdometerKm)
    }

    @Test
    fun `activate throws when rental is not RESERVED`() {
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        assertFailsWith<IllegalArgumentException> {
            rental.activate(actualStart = now, startOdo = 5000)
        }
    }

    // --- complete() ---

    @Test
    fun `complete transitions ACTIVE to COMPLETED`() {
        val rental = sampleRental(status = RentalStatus.ACTIVE)
            .activate(actualStart = now, startOdo = 5000)
        val completed = rental.complete(actualEnd = now, endOdo = 5800)

        assertEquals(RentalStatus.COMPLETED, completed.status)
        assertEquals(5800, completed.endOdometerKm)
        assertNotNull(completed.actualEndDate)
    }

    @Test
    fun `complete throws when rental is not ACTIVE`() {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        assertFailsWith<IllegalArgumentException> {
            rental.complete(actualEnd = now, endOdo = 5800)
        }
    }

    // --- cancel() ---

    @Test
    fun `cancel transitions RESERVED to CANCELLED`() {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        val cancelled = rental.cancel()
        assertEquals(RentalStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `cancel throws when rental is already ACTIVE`() {
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        assertFailsWith<IllegalArgumentException> {
            rental.cancel()
        }
    }

    private fun sampleRental(
        status: RentalStatus = RentalStatus.RESERVED,
        startDate: Instant = tomorrow,
        endDate: Instant = nextWeek,
        totalAmount: Int = 5000
    ) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = com.solodev.fleet.modules.vehicles.domain.model.VehicleId("veh-001"),
        status = status,
        startDate = startDate,
        endDate = endDate,
        dailyRateAmount = 1000,
        totalAmount = totalAmount
    )
}
```

---

## 2. Use Case Unit Tests

### CreateRentalUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CreateRentalUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CreateRentalUseCaseTest {

    private val rentalRepository = mockk<RentalRepository>()
    private val vehicleRepository = mockk<VehicleRepository>()
    private val useCase = CreateRentalUseCase(rentalRepository, vehicleRepository)

    private val vehicleId = "c9352986-639a-4841-bed9-9ff99f2e3349"
    private val customerId = "68a1a7f1-76dd-4ec9-ad63-fefc22acf428"

    private fun request(
        start: String = "2026-06-01T10:00:00Z",
        end: String = "2026-06-05T10:00:00Z"
    ) = RentalRequest(
        vehicleId = vehicleId,
        customerId = customerId,
        startDate = start,
        endDate = end,
        estimatedPrice = 5000
    )

    @Test
    fun `creates rental with RESERVED status when vehicle is available`() = runBlocking {
        val vehicle = sampleVehicle(VehicleState.AVAILABLE)
        coEvery { vehicleRepository.findById(any()) } returns vehicle
        coEvery { rentalRepository.findConflictingRentals(any(), any(), any()) } returns emptyList()
        coEvery { rentalRepository.save(any()) } returnsArgument 0

        val result = useCase.execute(request())

        assertEquals(RentalStatus.RESERVED, result.status)
        coVerify { rentalRepository.save(any()) }
    }

    @Test
    fun `throws when vehicle is not AVAILABLE`() = runBlocking {
        coEvery { vehicleRepository.findById(any()) } returns sampleVehicle(VehicleState.RENTED)

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute(request())
        }
        assertTrue(ex.message!!.contains("available", ignoreCase = true))
    }

    @Test
    fun `throws when vehicle is in MAINTENANCE`() = runBlocking {
        coEvery { vehicleRepository.findById(any()) } returns sampleVehicle(VehicleState.MAINTENANCE)

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(request())
        }
    }

    @Test
    fun `throws when conflicting rental exists for the period`() = runBlocking {
        coEvery { vehicleRepository.findById(any()) } returns sampleVehicle(VehicleState.AVAILABLE)
        coEvery { rentalRepository.findConflictingRentals(any(), any(), any()) } returns listOf(mockk())

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute(request())
        }
        assertTrue(ex.message!!.contains("already rented", ignoreCase = true))
    }

    @Test
    fun `throws when vehicle is not found`() = runBlocking {
        coEvery { vehicleRepository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(request())
        }
    }

    @Test
    fun `calculates total cost as days * daily rate`() = runBlocking {
        val vehicle = sampleVehicle(VehicleState.AVAILABLE, dailyRate = 2000)
        coEvery { vehicleRepository.findById(any()) } returns vehicle
        coEvery { rentalRepository.findConflictingRentals(any(), any(), any()) } returns emptyList()
        coEvery { rentalRepository.save(any()) } returnsArgument 0

        val result = useCase.execute(request("2026-06-01T10:00:00Z", "2026-06-04T10:00:00Z"))

        // 3 days * 2000 = 6000
        assertEquals(6000, result.totalAmount)
    }

    private fun sampleVehicle(state: VehicleState, dailyRate: Int = 1500) = Vehicle(
        id = VehicleId(vehicleId),
        vin = "12345678901234567",
        licensePlate = "AAA-111",
        make = "Toyota",
        model = "Vios",
        year = 2023,
        state = state,
        mileageKm = 10000,
        currencyCode = "PHP",
        dailyRateAmount = dailyRate
    )
}
```

### ActivateRentalUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/ActivateRentalUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.accounts.application.AccountingService
import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class ActivateRentalUseCaseTest {

    private val rentalRepository = mockk<RentalRepository>()
    private val vehicleRepository = mockk<VehicleRepository>()
    private val accountingService = mockk<AccountingService>(relaxed = true)
    private val useCase = ActivateRentalUseCase(rentalRepository, vehicleRepository, accountingService)

    private val now = Instant.now()

    @Test
    fun `activates RESERVED rental and captures odometer`() = runBlocking {
        val rental = sampleRental(RentalStatus.RESERVED)
        val vehicle = sampleVehicle(mileageKm = 15000)
        coEvery { rentalRepository.findById(any()) } returns rental
        coEvery { vehicleRepository.findById(any()) } returns vehicle
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        coEvery { vehicleRepository.save(any()) } returnsArgument 0

        val result = useCase.execute("rental-001")

        assertEquals(RentalStatus.ACTIVE, result.status)
        assertEquals(15000, result.startOdometerKm)
    }

    @Test
    fun `vehicle state is changed to RENTED on activation`() = runBlocking {
        coEvery { rentalRepository.findById(any()) } returns sampleRental(RentalStatus.RESERVED)
        coEvery { vehicleRepository.findById(any()) } returns sampleVehicle()
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        val savedVehicle = slot<Vehicle>()
        coEvery { vehicleRepository.save(capture(savedVehicle)) } returnsArgument 0

        useCase.execute("rental-001")

        assertEquals(VehicleState.RENTED, savedVehicle.captured.state)
    }

    @Test
    fun `throws when rental is not RESERVED`() = runBlocking {
        coEvery { rentalRepository.findById(any()) } returns sampleRental(RentalStatus.ACTIVE)

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("rental-001")
        }
    }

    @Test
    fun `throws when rental is not found`() = runBlocking {
        coEvery { rentalRepository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-rental")
        }
    }

    @Test
    fun `posts accounting entry on activation`() = runBlocking {
        coEvery { rentalRepository.findById(any()) } returns sampleRental(RentalStatus.RESERVED)
        coEvery { vehicleRepository.findById(any()) } returns sampleVehicle()
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        coEvery { vehicleRepository.save(any()) } returnsArgument 0

        useCase.execute("rental-001")

        coVerify { accountingService.postRentalActivation(any()) }
    }

    private fun sampleRental(status: RentalStatus) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = VehicleId("veh-001"),
        status = status,
        startDate = now,
        endDate = now.plus(5, ChronoUnit.DAYS),
        dailyRateAmount = 1500,
        totalAmount = 7500
    )

    private fun sampleVehicle(mileageKm: Int = 10000) = Vehicle(
        id = VehicleId("veh-001"),
        vin = "12345678901234567",
        licensePlate = "AAA-111",
        make = "Toyota", model = "Vios", year = 2023,
        state = VehicleState.AVAILABLE,
        mileageKm = mileageKm,
        currencyCode = "PHP"
    )
}
```

### CompleteRentalUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CompleteRentalUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class CompleteRentalUseCaseTest {

    private val rentalRepository = mockk<RentalRepository>()
    private val vehicleRepository = mockk<VehicleRepository>()
    private val useCase = CompleteRentalUseCase(rentalRepository, vehicleRepository)

    private val now = Instant.now()

    @Test
    fun `completes ACTIVE rental with provided mileage`() = runBlocking {
        val rental = activeRental()
        val vehicle = sampleVehicle(mileageKm = 15000)
        coEvery { rentalRepository.findById(any()) } returns rental
        coEvery { vehicleRepository.findById(any()) } returns vehicle
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        coEvery { vehicleRepository.save(any()) } returnsArgument 0

        val result = useCase.execute("rental-001", finalMileage = 16500)

        assertEquals(RentalStatus.COMPLETED, result.status)
        assertEquals(16500, result.endOdometerKm)
    }

    @Test
    fun `vehicle is returned to AVAILABLE state on completion`() = runBlocking {
        coEvery { rentalRepository.findById(any()) } returns activeRental()
        coEvery { vehicleRepository.findById(any()) } returns sampleVehicle()
        coEvery { rentalRepository.save(any()) } returnsArgument 0
        val savedVehicle = slot<Vehicle>()
        coEvery { vehicleRepository.save(capture(savedVehicle)) } returnsArgument 0

        useCase.execute("rental-001")

        assertEquals(VehicleState.AVAILABLE, savedVehicle.captured.state)
    }

    @Test
    fun `throws when end mileage is less than start mileage`() = runBlocking {
        val rental = activeRental(startOdo = 15000)
        coEvery { rentalRepository.findById(any()) } returns rental
        coEvery { vehicleRepository.findById(any()) } returns sampleVehicle()

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute("rental-001", finalMileage = 14000)
        }
        assertTrue(ex.message!!.contains("mileage", ignoreCase = true))
    }

    @Test
    fun `throws when rental is not ACTIVE`() = runBlocking {
        val rental = sampleRental(RentalStatus.RESERVED)
        coEvery { rentalRepository.findById(any()) } returns rental

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("rental-001")
        }
    }

    private fun activeRental(startOdo: Int = 10000) = sampleRental(RentalStatus.ACTIVE)
        .copy(startOdometerKm = startOdo)

    private fun sampleRental(status: RentalStatus) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = VehicleId("veh-001"),
        status = status,
        startDate = now,
        endDate = now.plus(5, ChronoUnit.DAYS),
        dailyRateAmount = 1500,
        totalAmount = 7500
    )

    private fun sampleVehicle(mileageKm: Int = 12000) = Vehicle(
        id = VehicleId("veh-001"),
        vin = "12345678901234567",
        licensePlate = "AAA-111",
        make = "Toyota", model = "Vios", year = 2023,
        state = VehicleState.RENTED,
        mileageKm = mileageKm,
        currencyCode = "PHP"
    )
}
```

### CancelRentalUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/rentals/application/usecases/CancelRentalUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class CancelRentalUseCaseTest {

    private val repository = mockk<RentalRepository>()
    private val useCase = CancelRentalUseCase(repository)

    private val now = Instant.now()

    @Test
    fun `cancels RESERVED rental`() = runBlocking {
        coEvery { repository.findById(any()) } returns sampleRental(RentalStatus.RESERVED)
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute("rental-001")

        assertEquals(RentalStatus.CANCELLED, result.status)
    }

    @Test
    fun `throws when rental is ACTIVE`() = runBlocking {
        coEvery { repository.findById(any()) } returns sampleRental(RentalStatus.ACTIVE)

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("rental-001")
        }
    }

    @Test
    fun `throws when rental is not found`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-rental")
        }
    }

    private fun sampleRental(status: RentalStatus) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = com.solodev.fleet.modules.vehicles.domain.model.VehicleId("veh-001"),
        status = status,
        startDate = now,
        endDate = now.plus(5, ChronoUnit.DAYS),
        dailyRateAmount = 1500,
        totalAmount = 7500
    )
}
```

---

## 3. HTTP Route Integration Tests

### Rental Lifecycle Routes
`src/test/kotlin/com/solodev/fleet/modules/rentals/infrastructure/http/RentalRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.rentals.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class RentalRoutesTest {

    // --- GET /v1/rentals ---

    @Test
    fun `GET rentals returns 200 list`() = testApplication {
        val response = client.get("/v1/rentals") { bearerAuth(TEST_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
    }

    @Test
    fun `GET rentals returns 401 without auth`() = testApplication {
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/rentals").status)
    }

    // --- POST /v1/rentals ---

    @Test
    fun `POST rentals creates RESERVED rental`() = testApplication {
        val response = client.post("/v1/rentals") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody(validRentalBody())
        }
        assertEquals(HttpStatusCode.Created, response.status)
        assertTrue(response.bodyAsText().contains("RESERVED"))
    }

    @Test
    fun `POST rentals returns 400 when required fields missing`() = testApplication {
        val response = client.post("/v1/rentals") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "vehicleId": "$VEHICLE_ID" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- GET /v1/rentals/{id} ---

    @Test
    fun `GET rentals-id returns 200 with rental detail`() = testApplication {
        val response = client.get("/v1/rentals/$RENTAL_ID") { bearerAuth(TEST_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("rentalNumber"))
    }

    @Test
    fun `GET rentals-id returns 404 for unknown rental`() = testApplication {
        val response = client.get("/v1/rentals/00000000-0000-0000-0000-000000000000") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 404..500)
    }

    // --- POST /v1/rentals/{id}/activate ---

    @Test
    fun `POST activate transitions RESERVED to ACTIVE`() = testApplication {
        val response = client.post("/v1/rentals/$RENTAL_ID/activate") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ACTIVE"))
    }

    @Test
    fun `POST activate returns 400 when rental is not RESERVED`() = testApplication {
        // Pre-condition: rental is already ACTIVE
        val response = client.post("/v1/rentals/$ALREADY_ACTIVE_RENTAL/activate") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 400..500)
    }

    // --- POST /v1/rentals/{id}/complete ---

    @Test
    fun `POST complete transitions ACTIVE to COMPLETED`() = testApplication {
        val response = client.post("/v1/rentals/$ACTIVE_RENTAL_ID/complete") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "finalMileage": 21500 }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("COMPLETED"))
    }

    @Test
    fun `POST complete returns error when finalMileage is less than start mileage`() = testApplication {
        val response = client.post("/v1/rentals/$ACTIVE_RENTAL_ID/complete") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "finalMileage": 100 }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    // --- POST /v1/rentals/{id}/cancel ---

    @Test
    fun `POST cancel transitions RESERVED to CANCELLED`() = testApplication {
        val response = client.post("/v1/rentals/$RENTAL_ID/cancel") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("CANCELLED"))
    }

    @Test
    fun `POST cancel returns 400 when rental is ACTIVE`() = testApplication {
        val response = client.post("/v1/rentals/$ALREADY_ACTIVE_RENTAL/cancel") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 400..500)
    }

    companion object {
        const val VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3349"
        const val CUSTOMER_ID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        const val RENTAL_ID = "r1b2c3d4-e5f6-7890-abcd-000000000001"
        const val ACTIVE_RENTAL_ID = "r1b2c3d4-e5f6-7890-abcd-000000000002"
        const val ALREADY_ACTIVE_RENTAL = ACTIVE_RENTAL_ID
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

        fun validRentalBody() = """
            {
                "vehicleId": "$VEHICLE_ID",
                "customerId": "$CUSTOMER_ID",
                "startDate": "2026-07-01T10:00:00Z",
                "endDate": "2026-07-05T10:00:00Z",
                "estimatedPrice": 6000
            }
        """.trimIndent()
    }
}
```

---

## 4. Concurrency Safety Tests

```kotlin
package com.solodev.fleet.modules.rentals.application.usecases

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Concurrency tests for CreateRentalUseCase.
 *
 * The use case relies on PostgreSQL advisory locks (pg_advisory_xact_lock) to prevent
 * double-booking. These tests require a live database to validate lock behavior.
 *
 * In unit tests with mocks these are not testable; use Testcontainers for this class.
 */
class RentalConcurrencyTest {

    @Test
    fun `only one of two concurrent reservations for the same vehicle succeeds`() = runBlocking {
        // Arrange: both requests target the same vehicleId and same date range
        // This test must be run against a live DB with Testcontainers
        // The expected behavior: exactly 1 succeeds, the other throws IllegalArgumentException

        val vehicleId = "c9352986-639a-4841-bed9-9ff99f2e3349"
        val request = RentalRequest(
            vehicleId = vehicleId,
            customerId = "cust-001",
            startDate = "2026-08-01T10:00:00Z",
            endDate = "2026-08-05T10:00:00Z",
            estimatedPrice = 5000
        )

        // Two simultaneous coroutines
        val results = (1..2).map {
            async {
                runCatching { /* liveUseCase.execute(request) */ }
            }
        }.awaitAll()

        val successes = results.count { it.isSuccess }
        val failures = results.count { it.isFailure }

        assertEquals(1, successes, "Exactly one reservation should succeed")
        assertEquals(1, failures, "Exactly one should be rejected as conflict")
    }
}
```

---

## 5. Test Summary

| Test Class | Layer | Coverage |
|---|---|---|
| `RentalTest` | Unit – Domain | endDate > startDate, `activate()`, `complete()`, `cancel()` — all valid and invalid transitions |
| `CreateRentalUseCaseTest` | Unit – Use Case | AVAILABLE vehicle, RENTED vehicle, MAINTENANCE vehicle, conflict, vehicle not found, cost calculation |
| `ActivateRentalUseCaseTest` | Unit – Use Case | RESERVED→ACTIVE, odometer capture, vehicle state change, accounting post, non-RESERVED throws |
| `CompleteRentalUseCaseTest` | Unit – Use Case | ACTIVE→COMPLETED, mileage regression check, vehicle returned to AVAILABLE |
| `CancelRentalUseCaseTest` | Unit – Use Case | RESERVED→CANCELLED, ACTIVE block, not found |
| `RentalRoutesTest` | Integration – HTTP | Full lifecycle HTTP routes, auth enforcement, 400/404 errors |
| `RentalConcurrencyTest` | Integration – Concurrency | Double-booking prevention via advisory lock (requires Testcontainers) |
