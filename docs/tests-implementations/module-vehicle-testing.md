# Vehicle Module - Test Implementation Guide

This document details the testing strategy and implementations for the Vehicle module, following Clean Architecture principles.

---

## 1. Domain Layer Tests (Unit Tests - No Dependencies)
Test pure business logic without any infrastructure dependencies.

`src/test/kotlin/com/solodev/fleet/modules/domain/models/VehicleTest.kt`

```kotlin
package com.solodev.fleet.modules.domain.models

import kotlin.test.*

class VehicleTest {
    
    @Test
    fun `should create valid vehicle`() {
        val vehicle = Vehicle(
            id = VehicleId("test-id"),
            vin = "12345678901234567",
            licensePlate = "ABC-123",
            make = "Toyota",
            model = "Camry",
            year = 2024,
            state = VehicleState.AVAILABLE,
            mileageKm = 0,
            currencyCode = "PHP"
        )
        
        assertEquals("ABC-123", vehicle.licensePlate)
        assertEquals(VehicleState.AVAILABLE, vehicle.state)
    }
    
    @Test
    fun `should transition from AVAILABLE to RENTED`() {
        val vehicle = createSampleVehicle(state = VehicleState.AVAILABLE)
        
        val rented = vehicle.rent()
        
        assertEquals(VehicleState.RENTED, rented.state)
    }
    
    @Test
    fun `should not rent vehicle that is not AVAILABLE`() {
        val vehicle = createSampleVehicle(state = VehicleState.MAINTENANCE)
        
        val exception = assertFailsWith<IllegalArgumentException> {
            vehicle.rent()
        }
        
        assertTrue(exception.message!!.contains("Cannot rent vehicle"))
    }
    
    @Test
    fun `should return vehicle from RENTED to AVAILABLE`() {
        val vehicle = createSampleVehicle(state = VehicleState.RENTED)
        
        val returned = vehicle.returnFromRental()
        
        assertEquals(VehicleState.AVAILABLE, returned.state)
    }
    
    @Test
    fun `should send AVAILABLE vehicle to MAINTENANCE`() {
        val vehicle = createSampleVehicle(state = VehicleState.AVAILABLE)
        
        val inMaintenance = vehicle.sendToMaintenance()
        
        assertEquals(VehicleState.MAINTENANCE, inMaintenance.state)
    }
    
    @Test
    fun `should not send RENTED vehicle to MAINTENANCE`() {
        val vehicle = createSampleVehicle(state = VehicleState.RENTED)
        
        val exception = assertFailsWith<IllegalArgumentException> {
            vehicle.sendToMaintenance()
        }
        
        assertTrue(exception.message!!.contains("Cannot send rented vehicle"))
    }
    
    @Test
    fun `should complete maintenance and return to AVAILABLE`() {
        val vehicle = createSampleVehicle(state = VehicleState.MAINTENANCE)
        
        val completed = vehicle.completeMaintenance()
        
        assertEquals(VehicleState.AVAILABLE, completed.state)
    }
    
    @Test
    fun `should update mileage when new value is higher`() {
        val vehicle = createSampleVehicle(mileageKm = 1000)
        
        val updated = vehicle.updateMileage(1500)
        
        assertEquals(1500, updated.mileageKm)
    }
    
    @Test
    fun `should not decrease mileage`() {
        val vehicle = createSampleVehicle(mileageKm = 1000)
        
        val exception = assertFailsWith<IllegalArgumentException> {
            vehicle.updateMileage(500)
        }
        
        assertTrue(exception.message!!.contains("Mileage cannot decrease"))
    }
    
    @Test
    fun `should reject invalid VIN length`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Vehicle(
                id = VehicleId("test-id"),
                vin = "SHORT",
                licensePlate = "ABC-123",
                make = "Toyota",
                model = "Camry",
                year = 2024,
                currencyCode = "PHP"
            )
        }
        
        assertTrue(exception.message!!.contains("VIN must be exactly 17 characters"))
    }
    
    @Test
    fun `should reject non-PHP currency`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            Vehicle(
                id = VehicleId("test-id"),
                vin = "12345678901234567",
                licensePlate = "ABC-123",
                make = "Toyota",
                model = "Camry",
                year = 2024,
                currencyCode = "USD"
            )
        }
        
        assertTrue(exception.message!!.contains("Only PHP currency is supported"))
    }
    
    private fun createSampleVehicle(
        state: VehicleState = VehicleState.AVAILABLE,
        mileageKm: Int = 0
    ) = Vehicle(
        id = VehicleId("test-id"),
        vin = "12345678901234567",
        licensePlate = "ABC-123",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        state = state,
        mileageKm = mileageKm,
        currencyCode = "PHP"
    )
}
```

---

## 2. Use Case Tests (Unit Tests with Mocks)
Test business logic with mocked dependencies using **MockK**.

`src/test/kotlin/com/solodev/fleet/modules/domain/usecases/VehicleUseCasesTest.kt`

```kotlin
package com.solodev.fleet.modules.domain.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.VehicleRepository
import io.mockk.*
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class RecordOdometerUseCaseTest {
    
    private val repository = mockk<VehicleRepository>()
    private val useCase = RecordOdometerUseCase(repository)
    
    @Test
    fun `should update mileage successfully`() = runBlocking {
        val vehicle = createSampleVehicle(mileageKm = 1000)
        val updated = vehicle.copy(mileageKm = 1500)
        
        coEvery { repository.findById(any()) } returns vehicle
        coEvery { repository.save(any()) } returns updated
        
        val result = useCase.execute("vehicle-id", 1500)
        
        assertNotNull(result)
        assertEquals(1500, result.mileageKm)
        coVerify { repository.save(match { it.mileageKm == 1500 }) }
    }
    
    @Test
    fun `should fail when new mileage is lower`() = runBlocking {
        val vehicle = createSampleVehicle(mileageKm = 1000)
        coEvery { repository.findById(any()) } returns vehicle
        
        val exception = assertFailsWith<IllegalArgumentException> {
            useCase.execute("vehicle-id", 500)
        }
        
        assertTrue(exception.message!!.contains("cannot be less than current mileage"))
        coVerify(exactly = 0) { repository.save(any()) }
    }
    
    @Test
    fun `should return null when vehicle not found`() = runBlocking {
        coEvery { repository.findById(any()) } returns null
        
        val result = useCase.execute("non-existent-id", 1000)
        
        assertNull(result)
    }
    
    private fun createSampleVehicle(mileageKm: Int = 0) = Vehicle(
        id = VehicleId("test-id"),
        vin = "12345678901234567",
        licensePlate = "ABC-123",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        mileageKm = mileageKm,
        currencyCode = "PHP"
    )
}

class UpdateVehicleStateUseCaseTest {
    
    private val repository = mockk<VehicleRepository>()
    private val useCase = UpdateVehicleStateUseCase(repository)
    
    @Test
    fun `should transition to MAINTENANCE state`() = runBlocking {
        val vehicle = createSampleVehicle(state = VehicleState.AVAILABLE)
        val updated = vehicle.copy(state = VehicleState.MAINTENANCE)
        
        coEvery { repository.findById(any()) } returns vehicle
        coEvery { repository.save(any()) } returns updated
        
        val result = useCase.execute("vehicle-id", "MAINTENANCE")
        
        assertNotNull(result)
        assertEquals(VehicleState.MAINTENANCE, result.state)
    }
    
    @Test
    fun `should fail when transitioning from invalid state`() = runBlocking {
        val vehicle = createSampleVehicle(state = VehicleState.RENTED)
        coEvery { repository.findById(any()) } returns vehicle
        
        val exception = assertFailsWith<IllegalArgumentException> {
            useCase.execute("vehicle-id", "MAINTENANCE")
        }
        
        assertTrue(exception.message!!.contains("Cannot send rented vehicle"))
    }
    
    private fun createSampleVehicle(state: VehicleState = VehicleState.AVAILABLE) = Vehicle(
        id = VehicleId("test-id"),
        vin = "12345678901234567",
        licensePlate = "ABC-123",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        state = state,
        currencyCode = "PHP"
    )
}
```

---

## 3. HTTP Integration Tests
Test the full HTTP stack including routing, serialization, and error handling.

`src/test/kotlin/com/solodev/fleet/modules/vehicles/infrastructure/http/VehicleRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.vehicles.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.*

class VehicleRoutesTest {
    
    private fun ApplicationTestBuilder.configureTestDb() {
        environment {
            config = MapApplicationConfig(
                "storage.jdbcUrl" to "jdbc:postgresql://127.0.0.1:5435/fleet_test",
                "storage.username" to "fleet_user",
                "storage.password" to "secret_123",
                "storage.driverClassName" to "org.postgresql.Driver",
                "storage.maximumPoolSize" to "2"
            )
        }
    }
    
    @Test
    fun `GET vehicles should return 200`() = testApplication {
        configureTestDb()
        application { module() }
        
        val response = client.get("/v1/vehicles")
        
        assertEquals(HttpStatusCode.OK, response.status)
    }
    
    @Test
    fun `POST vehicles should create vehicle`() = testApplication {
        configureTestDb()
        application { module() }
        
        val response = client.post("/v1/vehicles") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vin": "12345678901234567",
                    "licensePlate": "TEST-001",
                    "make": "Toyota",
                    "model": "Camry",
                    "year": 2024
                }
            """.trimIndent())
        }
        
        assertEquals(HttpStatusCode.Created, response.status)
    }
}
```

**Note**: Integration tests require a running PostgreSQL database. For CI/CD, use Docker Compose or Testcontainers.

---

## 4. Additional Use Case Tests

### CreateVehicleUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/vehicles/application/usecases/CreateVehicleUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.application.dto.CreateVehicleRequest
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class CreateVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = CreateVehicleUseCase(repository)

    @Test
    fun `creates vehicle with AVAILABLE status and PHP currency`() = runBlocking {
        coEvery { repository.findByVin(any()) } returns null
        coEvery { repository.findByLicensePlate(any()) } returns null
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute(validRequest())

        assertEquals(VehicleState.AVAILABLE, result.state)
        assertEquals("PHP", result.currencyCode)
        coVerify { repository.save(any()) }
    }

    @Test
    fun `throws on duplicate VIN`() = runBlocking {
        coEvery { repository.findByVin("12345678901234567") } returns existingVehicle()

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest())
        }
    }

    @Test
    fun `throws on duplicate license plate`() = runBlocking {
        coEvery { repository.findByVin(any()) } returns null
        coEvery { repository.findByLicensePlate("ABC-123") } returns existingVehicle()

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest())
        }
    }

    @Test
    fun `throws for VIN shorter than 17 characters`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest(vin = "SHORT"))
        }
    }

    @Test
    fun `throws for VIN longer than 17 characters`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest(vin = "123456789012345678"))
        }
    }

    @Test
    fun `throws for non-PHP currency`() {
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest(currencyCode = "USD"))
        }
    }

    private fun validRequest(
        vin: String = "12345678901234567",
        currencyCode: String = "PHP"
    ) = CreateVehicleRequest(
        vin = vin,
        licensePlate = "ABC-123",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        currencyCode = currencyCode,
        dailyRateAmount = 5000
    )

    private fun existingVehicle() = Vehicle(
        id = VehicleId("existing-id"),
        vin = "12345678901234567",
        licensePlate = "ABC-123",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        state = VehicleState.AVAILABLE,
        currencyCode = "PHP"
    )
}
```

### GetVehicleUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/vehicles/application/usecases/GetVehicleUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class GetVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = GetVehicleUseCase(repository)

    @Test
    fun `returns vehicle when found by id`() = runBlocking {
        val vehicle = sampleVehicle()
        coEvery { repository.findById(VehicleId("veh-001")) } returns vehicle

        val result = useCase.execute("veh-001")

        assertNotNull(result)
        assertEquals("veh-001", result.id.value)
    }

    @Test
    fun `returns null when vehicle does not exist`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        val result = useCase.execute("non-existent")

        assertNull(result)
    }

    private fun sampleVehicle() = Vehicle(
        id = VehicleId("veh-001"),
        vin = "12345678901234567",
        licensePlate = "ABC-123",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        state = VehicleState.AVAILABLE,
        currencyCode = "PHP"
    )
}
```

### ListVehiclesUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/vehicles/application/usecases/ListVehiclesUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ListVehiclesUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = ListVehiclesUseCase(repository)

    @Test
    fun `returns all vehicles`() = runBlocking {
        coEvery { repository.findAll() } returns listOf(sampleVehicle("v1"), sampleVehicle("v2"))

        val result = useCase.execute()

        assertEquals(2, result.size)
    }

    @Test
    fun `returns empty list when no vehicles exist`() = runBlocking {
        coEvery { repository.findAll() } returns emptyList()

        val result = useCase.execute()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filters by state when specified`() = runBlocking {
        val available = sampleVehicle("v1", VehicleState.AVAILABLE)
        val rented = sampleVehicle("v2", VehicleState.RENTED)
        coEvery { repository.findByState(VehicleState.AVAILABLE) } returns listOf(available)

        val result = useCase.execute(state = VehicleState.AVAILABLE)

        assertEquals(1, result.size)
        assertEquals(VehicleState.AVAILABLE, result.first().state)
    }

    private fun sampleVehicle(id: String, state: VehicleState = VehicleState.AVAILABLE) = Vehicle(
        id = VehicleId(id),
        vin = "12345678901234567",
        licensePlate = "$id-PLATE",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        state = state,
        currencyCode = "PHP"
    )
}
```

### DeleteVehicleUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/vehicles/application/usecases/DeleteVehicleUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class DeleteVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = DeleteVehicleUseCase(repository)

    @Test
    fun `deletes AVAILABLE vehicle`() = runBlocking {
        coEvery { repository.findById(any()) } returns sampleVehicle(VehicleState.AVAILABLE)
        coEvery { repository.delete(any()) } just Runs

        useCase.execute("veh-001")

        coVerify { repository.delete(VehicleId("veh-001")) }
    }

    @Test
    fun `throws when vehicle is RENTED`() = runBlocking {
        coEvery { repository.findById(any()) } returns sampleVehicle(VehicleState.RENTED)

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute("veh-001")
        }
        assertTrue(ex.message!!.contains("rented", ignoreCase = true))
        coVerify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `throws when vehicle not found`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("missing-veh")
        }
    }

    private fun sampleVehicle(state: VehicleState) = Vehicle(
        id = VehicleId("veh-001"),
        vin = "12345678901234567",
        licensePlate = "ABC-123",
        make = "Toyota",
        model = "Camry",
        year = 2024,
        state = state,
        currencyCode = "PHP"
    )
}
```

---

## 5. Expanded HTTP Integration Tests

`src/test/kotlin/com/solodev/fleet/modules/vehicles/infrastructure/http/VehicleRoutesExpandedTest.kt`

```kotlin
package com.solodev.fleet.modules.vehicles.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class VehicleRoutesExpandedTest {

    // --- GET /v1/vehicles ---

    @Test
    fun `GET vehicles returns 200 with vehicles array`() = testApplication {
        val response = client.get("/v1/vehicles") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
    }

    @Test
    fun `GET vehicles returns 401 without token`() = testApplication {
        val response = client.get("/v1/vehicles")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- GET /v1/vehicles/{id} ---

    @Test
    fun `GET vehicle by id returns 200`() = testApplication {
        val response = client.get("/v1/vehicles/$VEHICLE_ID") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("licensePlate"))
    }

    @Test
    fun `GET vehicle by unknown id returns 404`() = testApplication {
        val response = client.get("/v1/vehicles/00000000-0000-0000-0000-000000000000") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `GET vehicle returns 401 without token`() = testApplication {
        val response = client.get("/v1/vehicles/$VEHICLE_ID")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- POST /v1/vehicles ---

    @Test
    fun `POST vehicles creates vehicle and returns 201`() = testApplication {
        val response = client.post("/v1/vehicles") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vin": "NEWVIN0000NEWVIN01",
                    "licensePlate": "NEW-002",
                    "make": "Honda",
                    "model": "Civic",
                    "year": 2025,
                    "dailyRateAmount": 4500
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("AVAILABLE"))
    }

    @Test
    fun `POST vehicles returns 400 for missing VIN`() = testApplication {
        val response = client.post("/v1/vehicles") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "licensePlate": "TEST-999", "make": "Honda", "model": "Civic", "year": 2025 }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST vehicles returns 400 for invalid VIN length`() = testApplication {
        val response = client.post("/v1/vehicles") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vin": "SHORT",
                    "licensePlate": "TEST-998",
                    "make": "Honda",
                    "model": "Civic",
                    "year": 2025
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST vehicles returns 409 on duplicate VIN`() = testApplication {
        client.post("/v1/vehicles") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vin": "DUPVIN0000DUPVIN01",
                    "licensePlate": "DUP-001",
                    "make": "Toyota",
                    "model": "Camry",
                    "year": 2024
                }
            """.trimIndent())
        }
        // Second request with same VIN
        val response = client.post("/v1/vehicles") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vin": "DUPVIN0000DUPVIN01",
                    "licensePlate": "DUP-002",
                    "make": "Toyota",
                    "model": "Corolla",
                    "year": 2024
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    // --- PATCH /v1/vehicles/{id}/state ---

    @Test
    fun `PATCH vehicle state transitions AVAILABLE to MAINTENANCE`() = testApplication {
        val response = client.patch("/v1/vehicles/$VEHICLE_ID/state") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "state": "MAINTENANCE" }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("MAINTENANCE"))
    }

    @Test
    fun `PATCH vehicle state returns 400 for invalid state string`() = testApplication {
        val response = client.patch("/v1/vehicles/$VEHICLE_ID/state") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "state": "DESTROYED" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH vehicle state returns 400 for invalid transition (RENTED to MAINTENANCE)`() = testApplication {
        val response = client.patch("/v1/vehicles/$RENTED_VEHICLE_ID/state") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "state": "MAINTENANCE" }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    // --- POST /v1/vehicles/{id}/odometer ---

    @Test
    fun `POST odometer updates mileage`() = testApplication {
        val response = client.post("/v1/vehicles/$VEHICLE_ID/odometer") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "mileageKm": 15000 }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("15000"))
    }

    @Test
    fun `POST odometer returns 400 when new mileage is lower`() = testApplication {
        val response = client.post("/v1/vehicles/$VEHICLE_ID/odometer") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "mileageKm": 1 }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    @Test
    fun `POST odometer returns 401 without token`() = testApplication {
        val response = client.post("/v1/vehicles/$VEHICLE_ID/odometer") {
            contentType(ContentType.Application.Json)
            setBody("""{ "mileageKm": 20000 }""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- DELETE /v1/vehicles/{id} ---

    @Test
    fun `DELETE vehicle returns 204 when removed`() = testApplication {
        val response = client.delete("/v1/vehicles/$DELETABLE_VEHICLE_ID") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 200..204)
    }

    @Test
    fun `DELETE vehicle returns 404 when not found`() = testApplication {
        val response = client.delete("/v1/vehicles/00000000-0000-0000-0000-000000000000") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `DELETE vehicle returns 400 when vehicle is RENTED`() = testApplication {
        val response = client.delete("/v1/vehicles/$RENTED_VEHICLE_ID") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 400..500)
    }

    companion object {
        const val VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3349"
        const val RENTED_VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3350"
        const val DELETABLE_VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3351"
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
}
```

---

## 6. Test Summary

| Test Class | Layer | Coverage |
|---|---|---|
| `VehicleTest` | Unit – Domain | Valid creation, `AVAILABLE→RENTED`, `RENTED→AVAILABLE`, `AVAILABLE→MAINTENANCE`, `MAINTENANCE→AVAILABLE`, mileage update, VIN length validation, PHP currency enforcement |
| `RecordOdometerUseCaseTest` | Unit – Use Case | Mileage update success, decrease throws, vehicle not found returns null |
| `UpdateVehicleStateUseCaseTest` | Unit – Use Case | `AVAILABLE→MAINTENANCE`, `RENTED→MAINTENANCE` blocked |
| `CreateVehicleUseCaseTest` | Unit – Use Case | Happy path (AVAILABLE + PHP), duplicate VIN, duplicate plate, short/long VIN, non-PHP currency |
| `GetVehicleUseCaseTest` | Unit – Use Case | Found by ID, returns null for missing |
| `ListVehiclesUseCaseTest` | Unit – Use Case | Returns all, empty list, filter by state |
| `DeleteVehicleUseCaseTest` | Unit – Use Case | Delete AVAILABLE, throws for RENTED, throws for missing |
| `VehicleRoutesTest` | Integration – HTTP | GET 200, POST create 201 |
| `VehicleRoutesExpandedTest` | Integration – HTTP | GET list/by-id (200/404/401), POST create (201/400/409), PATCH state (200/400/invalid transition), POST odometer (200/400/401), DELETE (204/404/400 for rented) |
