package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.*

class CompleteMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = CompleteMaintenanceUseCase(repository)

    @Test
    fun `completes IN_PROGRESS maintenance job with costs`() = runBlocking {
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        coEvery { repository.findById(any()) } returns job
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute("maint-001", laborCost = 50.0, partsCost = 20.0)

        assertEquals(MaintenanceStatus.COMPLETED, result.status)
        assertEquals(5000, result.laborCost)
        assertEquals(2000, result.partsCost)
        assertNotNull(result.completedAt)
        coVerify { repository.saveJob(any()) }
    }

    @Test
    fun `throws when job is SCHEDULED (not started)`(): Unit = runBlocking {
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        coEvery { repository.findById(any()) } returns job

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("maint-001", laborCost = 10.0, partsCost = 5.0)
        }
    }

    @Test
    fun `throws when job not found`(): Unit = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-id", laborCost = 10.0, partsCost = 5.0)
        }
    }

    private fun sampleJob(status: MaintenanceStatus) = MaintenanceJob(
        id = MaintenanceJobId("maint-001"),
        jobNumber = "MAINT-001",
        vehicleId = VehicleId("veh-001"),
        jobType = MaintenanceJobType.ROUTINE,
        status = status,
        description = "Regular oil change",
        scheduledDate = Instant.now()
    )
}
