package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.*

class ListIncidentsUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ListIncidentsUseCase(repository)

    private val vehicleIdStr = "vehicle-123"
    private val vehicleId = VehicleId(vehicleIdStr)
    private val jobIdStr = "job-456"
    private val jobId = MaintenanceJobId(jobIdStr)

    private val incident1 = VehicleIncident(
        id = IncidentId(UUID.randomUUID()),
        vehicleId = vehicleId,
        title = "Flat Tire",
        description = "...",
        severity = IncidentSeverity.MEDIUM,
        status = IncidentStatus.REPORTED,
        reportedAt = Instant.now(),
        reportedByUserId = UUID.randomUUID(),
        odometerKm = 50000
    )

    private val incident2 = VehicleIncident(
        id = IncidentId(UUID.randomUUID()),
        vehicleId = vehicleId,
        title = "Engine Smoke",
        description = "...",
        severity = IncidentSeverity.HIGH,
        status = IncidentStatus.REPORTED,
        reportedAt = Instant.now(),
        reportedByUserId = null,
        odometerKm = 55000
    )

    @Test
    fun `should return all incidents for a vehicle`() = runBlocking {
        // Arrange
        coEvery { repository.findIncidentsByVehicleId(vehicleId) } returns listOf(incident1, incident2)

        // Act
        val result = useCase.getAllByVehicle(vehicleIdStr)

        // Assert
        assertThat(result).hasSize(2)
        coVerify(exactly = 1) { repository.findIncidentsByVehicleId(vehicleId) }
    }

    @Test
    fun `should return active incidents for a vehicle`() = runBlocking {
        // Arrange
        coEvery { repository.findActiveIncidentsByVehicleId(vehicleId) } returns listOf(incident1)

        // Act
        val result = useCase.getActiveByVehicle(vehicleIdStr)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("Flat Tire")
        coVerify(exactly = 1) { repository.findActiveIncidentsByVehicleId(vehicleId) }
    }

    @Test
    fun `should return incidents for a maintenance job`() = runBlocking {
        // Arrange
        coEvery { repository.findIncidentsByJobId(jobId) } returns listOf(incident2)

        // Act
        val result = useCase.getByJob(jobIdStr)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].title).isEqualTo("Engine Smoke")
        coVerify(exactly = 1) { repository.findIncidentsByJobId(jobId) }
    }
}
