# Maintenance Module - Test Implementation Guide

This document details the testing strategy and implementations for the Maintenance module, ensuring vehicle safety and maintenance schedule accuracy.

---

## 1. Testing (The Quality Shield)

### Unit Test: Schedule Maintenance (AAA Pattern)
```kotlin
class ScheduleMaintenanceUseCaseTest {
    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ScheduleMaintenanceUseCase(repository)

    @Test
    fun `should successfully schedule a maintenance job`() = runBlocking {
        // Arrange
        val request = MaintenanceRequest(
            vehicleId = "v-123",
            jobType = "ROUTINE",
            description = "Annual oil and filter change",
            scheduledDate = "2026-12-01T10:00:00Z"
        )
        coEvery { repository.saveJob(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(request)

        // Assert
        assertEquals(MaintenanceStatus.SCHEDULED, result.status)
        assertEquals("v-123", result.vehicleId.value)
        coVerify(exactly = 1) { repository.saveJob(any()) }
    }
}


class StartMaintenanceUseCaseTest {
    private val repository = mockk<MaintenanceRepository>()
    private val useCase = StartMaintenanceUseCase(repository)

    @Test
    fun `should transition job to IN_PROGRESS`() = runBlocking {
        val job = MaintenanceJob(
            id = MaintenanceJobId("job-1"),
            jobNumber = "M-1",
            vehicleId = VehicleId("v-1"),
            status = MaintenanceStatus.SCHEDULED,
            jobType = MaintenanceJobType.ROUTINE,
            description = "Test",
            scheduledDate = Instant.now()
        )
        coEvery { repository.findById(any()) } returns job
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute("job-1")

        assertEquals(MaintenanceStatus.IN_PROGRESS, result.status)
    }
}

class CompleteMaintenanceUseCaseTest {
    private val repository = mockk<MaintenanceRepository>()
    private val useCase = CompleteMaintenanceUseCase(repository)

    @Test
    fun `should transition job to COMPLETED and set costs`() = runBlocking {
        val job = MaintenanceJob(
            id = MaintenanceJobId("job-1"),
            jobNumber = "M-1",
            vehicleId = VehicleId("v-1"),
            status = MaintenanceStatus.IN_PROGRESS,
            jobType = MaintenanceJobType.ROUTINE,
            description = "Test",
            scheduledDate = Instant.now(),
            startedAt = Instant.now()
        )
        coEvery { repository.findById(any()) } returns job
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute("job-1", 5000, 2000)

        assertEquals(MaintenanceStatus.COMPLETED, result.status)
        assertEquals(7000, result.totalCostCents)
    }
}
```

### HTTP Integration Tests (`MaintenanceRoutesTest`)
```kotlin
@Test
fun `POST maintenance should schedule job`() = testApplication {
    configureTestDb()
    val response = client.post("/v1/maintenance") {
        contentType(ContentType.Application.Json)
        setBody("""
            {
                "vehicleId": "v-123",
                "jobType": "ROUTINE",
                "description": "Oil Change",
                "scheduledDate": "2026-12-01T10:00:00Z"
            }
        """.trimIndent())
    }
    assertEquals(HttpStatusCode.Created, response.status)
}
```
