package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.*

class StartMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = StartMaintenanceUseCase(repository)

    @Test
    fun `starts scheduled maintenance job`() = runBlocking {
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        coEvery { repository.findById(any()) } returns job
        coEvery { repository.saveJob(any()) } returnsArgument 0

        val result = useCase.execute("maint-001")

        assertEquals(MaintenanceStatus.IN_PROGRESS, result.status)
        assertNotNull(result.startedAt)
        coVerify { repository.saveJob(any()) }
    }

    @Test
    fun `throws when job not found`(): Unit = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-id")
        }
    }

    @Test
    fun `throws when job is already IN_PROGRESS`(): Unit = runBlocking {
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        coEvery { repository.findById(any()) } returns job

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("maint-001")
        }
    }

    @Test
    fun `throws when job is CANCELLED`(): Unit = runBlocking {
        val job = sampleJob(status = MaintenanceStatus.CANCELLED)
        coEvery { repository.findById(any()) } returns job

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("maint-001")
        }
    }

    private fun sampleJob(status: MaintenanceStatus) = MaintenanceJob(
        id = MaintenanceJobId("maint-001"),
        jobNumber = "MAINT-001",
        vehicleId = VehicleId("veh-001"),
        status = status,
        jobType = MaintenanceJobType.ROUTINE,
        description = "Regular oil change",
        scheduledDate = Instant.now()
    )
}
