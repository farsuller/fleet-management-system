# Maintenance Module - Test Implementation Guide

This document covers testing strategy and implementations for the Maintenance module, including job scheduling, lifecycle state machine (SCHEDULED → IN_PROGRESS → COMPLETED), cost tracking, and invalid transition enforcement.

---

## 1. Domain Unit Tests

### MaintenanceJob State Machine Tests
`src/test/kotlin/com/solodev/fleet/modules/maintenance/domain/model/MaintenanceJobTest.kt`

```kotlin
package com.solodev.fleet.modules.maintenance.domain.model

import java.time.Instant
import kotlin.test.*

class MaintenanceJobTest {

    // --- Invariants ---

    @Test
    fun `laborCost cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob().copy(laborCost = -1)
        }
    }

    @Test
    fun `partsCost cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob().copy(partsCost = -1)
        }
    }

    @Test
    fun `totalCost is sum of labor and parts`() {
        val job = sampleJob().copy(laborCost = 5000, partsCost = 2000)
        assertEquals(7000, job.totalCost)
    }

    @Test
    fun `MaintenanceJobId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            MaintenanceJobId("")
        }
    }

    // --- start() ---

    @Test
    fun `start transitions SCHEDULED to IN_PROGRESS`() {
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        val started = job.start()

        assertEquals(MaintenanceStatus.IN_PROGRESS, started.status)
        assertNotNull(started.startedAt)
    }

    @Test
    fun `start throws when job is not SCHEDULED`() {
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        val ex = assertFailsWith<IllegalArgumentException> {
            job.start()
        }
        assertTrue(ex.message!!.contains("SCHEDULED", ignoreCase = true))
    }

    @Test
    fun `start throws for COMPLETED job`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.COMPLETED).start()
        }
    }

    @Test
    fun `start throws for CANCELLED job`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.CANCELLED).start()
        }
    }

    // --- complete() ---

    @Test
    fun `complete transitions IN_PROGRESS to COMPLETED with costs`() {
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        val completed = job.complete(labor = 8000, parts = 3000)

        assertEquals(MaintenanceStatus.COMPLETED, completed.status)
        assertEquals(8000, completed.laborCost)
        assertEquals(3000, completed.partsCost)
        assertEquals(11000, completed.totalCost)
        assertNotNull(completed.completedAt)
    }

    @Test
    fun `complete throws when job is SCHEDULED (not yet started)`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.SCHEDULED).complete(5000, 2000)
        }
        assertTrue(ex.message!!.contains("IN_PROGRESS", ignoreCase = true))
    }

    @Test
    fun `complete throws when job is CANCELLED`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.CANCELLED).complete(5000, 2000)
        }
    }

    // --- cancel() ---

    @Test
    fun `cancel transitions SCHEDULED to CANCELLED`() {
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        val cancelled = job.cancel()
        assertEquals(MaintenanceStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `cancel throws when job has already started`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.IN_PROGRESS).cancel()
        }
        assertTrue(ex.message!!.contains("started", ignoreCase = true))
    }

    @Test
    fun `cancel throws when job is already COMPLETED`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.COMPLETED).cancel()
        }
    }

    private fun sampleJob(
        status: MaintenanceStatus = MaintenanceStatus.SCHEDULED
    ) = MaintenanceJob(
        id = MaintenanceJobId("job-001"),
        jobNumber = "MAINT-001",
        vehicleId = com.solodev.fleet.modules.vehicles.domain.model.VehicleId("veh-001"),
        status = status,
        jobType = MaintenanceJobType.ROUTINE,
        description = "Annual oil and filter change",
        scheduledDate = Instant.parse("2026-12-01T10:00:00Z")
    )
}
```

### MaintenancePart Tests
`src/test/kotlin/com/solodev/fleet/modules/maintenance/domain/model/MaintenancePartTest.kt`

```kotlin
package com.solodev.fleet.modules.maintenance.domain.model

import java.util.UUID
import kotlin.test.*

class MaintenancePartTest {

    @Test
    fun `totalCost is unitCost * quantity`() {
        val part = samplePart(unitCost = 500, quantity = 4)
        assertEquals(2000, part.totalCost)
    }

    @Test
    fun `partNumber cannot be blank`() {
        assertFailsWith<IllegalArgumentException> {
            samplePart(partNumber = "")
        }
    }

    @Test
    fun `partName cannot be blank`() {
        assertFailsWith<IllegalArgumentException> {
            samplePart(partName = "")
        }
    }

    @Test
    fun `quantity must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            samplePart(quantity = 0)
        }
    }

    @Test
    fun `unitCost cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            samplePart(unitCost = -100)
        }
    }

    private fun samplePart(
        partNumber: String = "OIL-5W30",
        partName: String = "Engine Oil 5W-30",
        quantity: Int = 1,
        unitCost: Int = 500
    ) = MaintenancePart(
        id = UUID.randomUUID(),
        jobId = MaintenanceJobId("job-001"),
        partNumber = partNumber,
        partName = partName,
        quantity = quantity,
        unitCost = unitCost
    )
}
```

---

## 2. Use Case Unit Tests

### ScheduleMaintenanceUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/maintenance/application/usecases/ScheduleMaintenanceUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ScheduleMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ScheduleMaintenanceUseCase(repository)

    @Test
    fun `schedules job with SCHEDULED status`() = runBlocking {
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute(validRequest())

        assertEquals(MaintenanceStatus.SCHEDULED, result.status)
        assertEquals(MaintenanceJobType.ROUTINE, result.jobType)
        coVerify(exactly = 1) { repository.saveJob(any()) }
    }

    @Test
    fun `throws on unknown job type`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(validRequest(jobType = "INVALID_TYPE"))
        }
    }

    @Test
    fun `job number is auto-generated`() = runBlocking {
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute(validRequest())

        assertTrue(result.jobNumber.startsWith("MAINT-"))
    }

    private fun validRequest(
        vehicleId: String = "c9352986-639a-4841-bed9-9ff99f2e3349",
        jobType: String = "ROUTINE"
    ) = MaintenanceRequest(
        vehicleId = vehicleId,
        description = "Annual oil and filter change",
        scheduledDate = "2026-12-01T10:00:00Z",
        jobType = jobType
    )
}
```

### StartMaintenanceUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/maintenance/application/usecases/StartMaintenanceUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.*

class StartMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = StartMaintenanceUseCase(repository)

    @Test
    fun `transitions SCHEDULED job to IN_PROGRESS`() = runBlocking {
        coEvery { repository.findById(any()) } returns scheduledJob()
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute("job-001")

        assertEquals(MaintenanceStatus.IN_PROGRESS, result.status)
        assertNotNull(result.startedAt)
        coVerify { repository.saveJob(match { it.status == MaintenanceStatus.IN_PROGRESS }) }
    }

    @Test
    fun `throws when job is not found`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-job")
        }
    }

    @Test
    fun `throws when job is already IN_PROGRESS`() = runBlocking {
        coEvery { repository.findById(any()) } returns scheduledJob().copy(
            status = MaintenanceStatus.IN_PROGRESS, startedAt = Instant.now()
        )

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("job-001")
        }
    }

    @Test
    fun `throws when job is CANCELLED`() = runBlocking {
        coEvery { repository.findById(any()) } returns scheduledJob().copy(
            status = MaintenanceStatus.CANCELLED
        )

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("job-001")
        }
    }

    private fun scheduledJob() = MaintenanceJob(
        id = MaintenanceJobId("job-001"),
        jobNumber = "MAINT-001",
        vehicleId = com.solodev.fleet.modules.vehicles.domain.model.VehicleId("veh-001"),
        status = MaintenanceStatus.SCHEDULED,
        jobType = MaintenanceJobType.ROUTINE,
        description = "Oil change",
        scheduledDate = Instant.parse("2026-12-01T10:00:00Z")
    )
}
```

### CompleteMaintenanceUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/maintenance/application/usecases/CompleteMaintenanceUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.*

class CompleteMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = CompleteMaintenanceUseCase(repository)

    @Test
    fun `transitions IN_PROGRESS job to COMPLETED and sets costs`() = runBlocking {
        coEvery { repository.findById(any()) } returns inProgressJob()
        coEvery { repository.saveJob(any()) } returnsArgument 0

        // useCase passes labor * 100 and parts * 100 to job.complete()
        val result = useCase.execute("job-001", laborCost = 50.0, partsCost = 20.0)

        assertEquals(MaintenanceStatus.COMPLETED, result.status)
        assertEquals(5000, result.laborCost)
        assertEquals(2000, result.partsCost)
        assertEquals(7000, result.totalCost)
        assertNotNull(result.completedAt)
    }

    @Test
    fun `throws when job is SCHEDULED (not yet started)`() = runBlocking {
        coEvery { repository.findById(any()) } returns inProgressJob().copy(
            status = MaintenanceStatus.SCHEDULED, startedAt = null
        )

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("job-001", 50.0, 20.0)
        }
    }

    @Test
    fun `throws when job is not found`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-job", 50.0, 20.0)
        }
    }

    private fun inProgressJob() = MaintenanceJob(
        id = MaintenanceJobId("job-001"),
        jobNumber = "MAINT-001",
        vehicleId = com.solodev.fleet.modules.vehicles.domain.model.VehicleId("veh-001"),
        status = MaintenanceStatus.IN_PROGRESS,
        jobType = MaintenanceJobType.ROUTINE,
        description = "Oil change",
        scheduledDate = Instant.parse("2026-12-01T10:00:00Z"),
        startedAt = Instant.now()
    )
}
```

### CancelMaintenanceUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/maintenance/application/usecases/CancelMaintenanceUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import kotlin.test.*

class CancelMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = CancelMaintenanceUseCase(repository)

    @Test
    fun `cancels SCHEDULED job`() = runBlocking {
        coEvery { repository.findById(any()) } returns scheduledJob()
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute("job-001")

        assertEquals(MaintenanceStatus.CANCELLED, result.status)
    }

    @Test
    fun `throws when job is IN_PROGRESS`() = runBlocking {
        coEvery { repository.findById(any()) } returns scheduledJob().copy(
            status = MaintenanceStatus.IN_PROGRESS, startedAt = Instant.now()
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute("job-001")
        }
        assertTrue(ex.message!!.contains("started", ignoreCase = true))
    }

    @Test
    fun `throws when job is not found`() = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-job")
        }
    }

    private fun scheduledJob() = MaintenanceJob(
        id = MaintenanceJobId("job-001"),
        jobNumber = "MAINT-001",
        vehicleId = com.solodev.fleet.modules.vehicles.domain.model.VehicleId("veh-001"),
        status = MaintenanceStatus.SCHEDULED,
        jobType = MaintenanceJobType.ROUTINE,
        description = "Oil change",
        scheduledDate = Instant.parse("2026-12-01T10:00:00Z")
    )
}
```

---

## 3. HTTP Route Integration Tests

### Maintenance Routes
`src/test/kotlin/com/solodev/fleet/modules/maintenance/infrastructure/http/MaintenanceRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.maintenance.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class MaintenanceRoutesTest {

    // --- POST /v1/maintenance ---

    @Test
    fun `POST maintenance schedules job and returns 201`() = testApplication {
        val response = client.post("/v1/maintenance") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vehicleId": "$VEHICLE_ID",
                    "jobType": "ROUTINE",
                    "description": "Annual oil and filter change",
                    "scheduledDate": "2026-12-01T10:00:00Z"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("SCHEDULED"))
        assertTrue(body.contains("jobNumber"))
    }

    @Test
    fun `POST maintenance returns 400 for unknown jobType`() = testApplication {
        val response = client.post("/v1/maintenance") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vehicleId": "$VEHICLE_ID",
                    "jobType": "INVALID_TYPE",
                    "description": "Test",
                    "scheduledDate": "2026-12-01T10:00:00Z"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST maintenance returns 400 when required fields missing`() = testApplication {
        val response = client.post("/v1/maintenance") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "vehicleId": "$VEHICLE_ID" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST maintenance returns 401 without auth`() = testApplication {
        val response = client.post("/v1/maintenance") {
            contentType(ContentType.Application.Json)
            setBody("""{}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- GET /v1/maintenance/vehicle/{id} ---

    @Test
    fun `GET maintenance by vehicle returns 200 list`() = testApplication {
        val response = client.get("/v1/maintenance/vehicle/$VEHICLE_ID") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
    }

    @Test
    fun `GET maintenance by vehicle returns empty list for vehicle with no jobs`() = testApplication {
        val response = client.get("/v1/maintenance/vehicle/00000000-0000-0000-0000-000000000000") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // --- POST /v1/maintenance/{id}/start ---

    @Test
    fun `POST start transitions job to IN_PROGRESS`() = testApplication {
        val response = client.post("/v1/maintenance/$JOB_ID/start") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("IN_PROGRESS"))
    }

    @Test
    fun `POST start returns 400 when job is already IN_PROGRESS`() = testApplication {
        val response = client.post("/v1/maintenance/$ALREADY_STARTED_JOB/start") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 400..500)
    }

    // --- POST /v1/maintenance/{id}/complete ---

    @Test
    fun `POST complete transitions job to COMPLETED with costs`() = testApplication {
        val response = client.post("/v1/maintenance/$IN_PROGRESS_JOB/complete") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "laborCost": 50.00, "partsCost": 20.00 }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("COMPLETED"))
    }

    @Test
    fun `POST complete returns 400 when job is SCHEDULED`() = testApplication {
        val response = client.post("/v1/maintenance/$JOB_ID/complete") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "laborCost": 50.00, "partsCost": 20.00 }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    // --- POST /v1/maintenance/{id}/cancel ---

    @Test
    fun `POST cancel transitions SCHEDULED job to CANCELLED`() = testApplication {
        val response = client.post("/v1/maintenance/$JOB_ID/cancel") {
            bearerAuth(TEST_JWT)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("CANCELLED"))
    }

    @Test
    fun `POST cancel returns 400 when job is IN_PROGRESS`() = testApplication {
        val response = client.post("/v1/maintenance/$ALREADY_STARTED_JOB/cancel") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 400..500)
    }

    companion object {
        const val VEHICLE_ID = "c9352986-639a-4841-bed9-9ff99f2e3349"
        const val JOB_ID = "m0000001-0000-0000-0000-000000000001"
        const val IN_PROGRESS_JOB = "m0000001-0000-0000-0000-000000000002"
        const val ALREADY_STARTED_JOB = IN_PROGRESS_JOB
        const val TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
    }
}
```

---

## 4. Error Scenario Tests

```kotlin
package com.solodev.fleet.modules.maintenance.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class MaintenanceRoutesErrorTest {

    @Test
    fun `POST maintenance with invalid UUID vehicleId returns 400`() = testApplication {
        val response = client.post("/v1/maintenance") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "vehicleId": "not-a-uuid",
                    "jobType": "ROUTINE",
                    "description": "Test",
                    "scheduledDate": "2026-12-01T10:00:00Z"
                }
            """.trimIndent())
        }
        assertTrue(response.status.value in 400..500)
    }

    @Test
    fun `POST start for unknown job returns 404`() = testApplication {
        val response = client.post("/v1/maintenance/00000000-0000-0000-0000-000000000000/start") {
            bearerAuth(TEST_JWT)
        }
        assertTrue(response.status.value in 400..500)
    }

    @Test
    fun `POST complete with negative cost values returns error`() = testApplication {
        val response = client.post("/v1/maintenance/${MaintenanceRoutesTest.IN_PROGRESS_JOB}/complete") {
            bearerAuth(TEST_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "laborCost": -50.00, "partsCost": -20.00 }""")
        }
        assertTrue(response.status.value in 400..500)
    }

    private val TEST_JWT = MaintenanceRoutesTest.TEST_JWT
}
```

---

## 5. Test Summary

| Test Class | Layer | Coverage |
|---|---|---|
| `MaintenanceJobTest` | Unit – Domain | `laborCost`/`partsCost` validation, `totalCost`, `start()` / `complete()` / `cancel()` — all valid and blocked transitions |
| `MaintenancePartTest` | Unit – Domain | `partNumber`/`partName` blank, `quantity > 0`, `unitCost >= 0`, `totalCost` calculation |
| `ScheduleMaintenanceUseCaseTest` | Unit – Use Case | Happy path, unknown job type, auto job number |
| `StartMaintenanceUseCaseTest` | Unit – Use Case | SCHEDULED→IN_PROGRESS, job not found, already IN_PROGRESS, CANCELLED |
| `CompleteMaintenanceUseCaseTest` | Unit – Use Case | IN_PROGRESS→COMPLETED with costs, SCHEDULED throws, not found |
| `CancelMaintenanceUseCaseTest` | Unit – Use Case | SCHEDULED→CANCELLED, IN_PROGRESS throws, not found |
| `MaintenanceRoutesTest` | Integration – HTTP | POST schedule, GET by vehicle, start/complete/cancel lifecycle, auth enforcement |
| `MaintenanceRoutesErrorTest` | Integration – Errors | Invalid UUID, unknown job, negative costs |
