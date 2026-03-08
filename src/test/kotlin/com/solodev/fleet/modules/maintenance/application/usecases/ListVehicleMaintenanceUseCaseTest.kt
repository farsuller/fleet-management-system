package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ListVehicleMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ListVehicleMaintenanceUseCase(repository)

    private val vehicleId = "vehicle-1"

    private val job1 = MaintenanceJob(
        id = MaintenanceJobId("job-1"),
        jobNumber = "MNT-001",
        vehicleId = VehicleId(vehicleId),
        status = MaintenanceStatus.SCHEDULED,
        jobType = MaintenanceJobType.ROUTINE,
        description = "Oil change",
        scheduledDate = Instant.parse("2026-04-01T00:00:00Z")
    )
    private val job2 = MaintenanceJob(
        id = MaintenanceJobId("job-2"),
        jobNumber = "MNT-002",
        vehicleId = VehicleId(vehicleId),
        status = MaintenanceStatus.COMPLETED,
        jobType = MaintenanceJobType.REPAIR,
        description = "Brake replacement",
        scheduledDate = Instant.parse("2026-03-01T00:00:00Z")
    )

    @Test
    fun shouldReturnJobs_WhenVehicleHasMaintenance() = runBlocking {
        // Arrange
        coEvery { repository.findByVehicleId(VehicleId(vehicleId)) } returns listOf(job1, job2)

        // Act
        val result = useCase.execute(vehicleId)

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { it.jobNumber }).containsExactly("MNT-001", "MNT-002")
    }

    @Test
    fun shouldReturnEmptyList_WhenVehicleHasNoMaintenance() = runBlocking {
        // Arrange
        coEvery { repository.findByVehicleId(VehicleId(vehicleId)) } returns emptyList()

        // Act
        val result = useCase.execute(vehicleId)

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun shouldQueryByCorrectVehicleId_WhenVehicleIdIsProvided() = runBlocking {
        // Arrange
        val capturedVehicleId = slot<VehicleId>()
        coEvery { repository.findByVehicleId(capture(capturedVehicleId)) } returns listOf(job1)

        // Act
        useCase.execute("vehicle-1")

        // Assert
        assertThat(capturedVehicleId.captured.value).isEqualTo("vehicle-1")
    }
}
