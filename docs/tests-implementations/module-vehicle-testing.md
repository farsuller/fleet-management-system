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
