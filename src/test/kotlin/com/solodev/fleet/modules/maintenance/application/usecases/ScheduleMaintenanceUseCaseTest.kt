package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class ScheduleMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ScheduleMaintenanceUseCase(repository)

    @Test
    fun `schedules maintenance job successfully`() = runBlocking {
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val request = MaintenanceRequest(
            vehicleId = "veh-001",
            jobType = "ROUTINE",
            description = "Regular oil change service",
            scheduledDate = "2026-03-10T00:00:00Z"
        )

        val result = useCase.execute(request)

        assertEquals(VehicleId("veh-001"), result.vehicleId)
        assertEquals(MaintenanceStatus.SCHEDULED, result.status)
        coVerify { repository.saveJob(any()) }
    }

    @Test
    fun `throws for unknown job type`(): Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            val request = MaintenanceRequest(
                vehicleId = "veh-001",
                jobType = "UNKNOWN_TYPE",
                description = "Some maintenance description here",
                scheduledDate = "2026-03-10T00:00:00Z"
            )
            useCase.execute(request)
        }
    }

    @Test
    fun `auto-generates job number`() = runBlocking {
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val request = MaintenanceRequest(
            vehicleId = "veh-001",
            jobType = "ROUTINE",
            description = "Regular oil change service",
            scheduledDate = "2026-03-10T00:00:00Z"
        )

        val result = useCase.execute(request)

        assertTrue(result.jobNumber.startsWith("MAINT-"))
    }
}
