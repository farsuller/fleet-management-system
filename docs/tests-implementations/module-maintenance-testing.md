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
```
