package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class StartMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = StartMaintenanceUseCase(repository)

    @Test
    fun shouldStartMaintenanceJob_WhenStatusIsScheduled() = runBlocking {
        // Arrange
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        val savedJob = slot<MaintenanceJob>()
        coEvery { repository.findById(MaintenanceJobId("maint-001")) } returns job
        coEvery { repository.saveJob(capture(savedJob)) } returnsArgument 0

        // Act
        val result = useCase.execute("maint-001")

        // Assert
        assertThat(result.status).isEqualTo(MaintenanceStatus.IN_PROGRESS)
        assertThat(result.startedAt).isNotNull()
        assertThat(savedJob.captured.status).isEqualTo(MaintenanceStatus.IN_PROGRESS)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenJobNotFound() {
        // Arrange
        coEvery { repository.findById(MaintenanceJobId("unknown-id")) } returns null

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("unknown-id") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenJobAlreadyInProgress() {
        // Arrange
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        coEvery { repository.findById(MaintenanceJobId("maint-001")) } returns job

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("maint-001") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenJobIsCancelled() {
        // Arrange
        val job = sampleJob(status = MaintenanceStatus.CANCELLED)
        coEvery { repository.findById(MaintenanceJobId("maint-001")) } returns job

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("maint-001") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sampleJob(status: MaintenanceStatus) = MaintenanceJob(
        id = MaintenanceJobId("maint-001"),
        jobNumber = "MAINT-001",
        vehicleId = VehicleId("veh-001"),
        status = status,
        jobType = MaintenanceJobType.PREVENTIVE,
        description = "Regular oil change",
        scheduledDate = Instant.now()
    )
}
