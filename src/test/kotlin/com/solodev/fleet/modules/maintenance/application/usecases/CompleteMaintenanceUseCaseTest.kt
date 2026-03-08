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

class CompleteMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = CompleteMaintenanceUseCase(repository)

    @Test
    fun shouldCompleteMaintenanceJob_WhenStatusIsInProgress() = runBlocking {
        // Arrange
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        val savedJob = slot<MaintenanceJob>()
        coEvery { repository.findById(MaintenanceJobId("maint-001")) } returns job
        coEvery { repository.saveJob(capture(savedJob)) } returnsArgument 0

        // Act
        val result = useCase.execute("maint-001", laborCost = 50.0, partsCost = 20.0)

        // Assert
        assertThat(result.status).isEqualTo(MaintenanceStatus.COMPLETED)
        assertThat(result.laborCost).isEqualTo(5000)
        assertThat(result.partsCost).isEqualTo(2000)
        assertThat(result.completedAt).isNotNull()
        assertThat(savedJob.captured.status).isEqualTo(MaintenanceStatus.COMPLETED)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenJobIsScheduled() {
        // Arrange
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        coEvery { repository.findById(MaintenanceJobId("maint-001")) } returns job

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("maint-001", laborCost = 10.0, partsCost = 5.0) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenJobNotFound() {
        // Arrange
        coEvery { repository.findById(MaintenanceJobId("unknown-id")) } returns null

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("unknown-id", laborCost = 10.0, partsCost = 5.0) } }
            .isInstanceOf(IllegalArgumentException::class.java)
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
