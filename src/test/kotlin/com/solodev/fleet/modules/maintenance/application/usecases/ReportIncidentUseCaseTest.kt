package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.IncidentSeverity
import com.solodev.fleet.modules.maintenance.domain.model.IncidentStatus
import com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ReportIncidentUseCaseTest {
    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ReportIncidentUseCase(repository)

    private val plate = "B 1234 XYZ"
    private val title = "Flat Tire"
    private val description = "Right front tire is flat"
    private val severity = IncidentSeverity.MEDIUM
    private val userId = UUID.randomUUID()

    @Test
    fun `should save and return incident when reporting is successful`() =
        runBlocking {
            // Arrange
            val capturedIncident = slot<VehicleIncident>()
            coEvery { repository.saveIncident(capture(capturedIncident)) } answers { firstArg() }

            // Act
            val result =
                useCase(
                    vehiclePlate = plate,
                    title = title,
                    description = description,
                    severity = severity,
                    reportedByUserId = userId,
                    odometerKm = 45000,
                    latitude = -6.2,
                    longitude = 106.8,
                )

            // Assert
            assertThat(result.isSuccess).isTrue()
            val incident = result.getOrThrow()

            assertThat(incident.vehicleId.value).isEqualTo(plate)
            assertThat(incident.title).isEqualTo(title)
            assertThat(incident.description).isEqualTo(description)
            assertThat(incident.severity).isEqualTo(severity)
            assertThat(incident.status).isEqualTo(IncidentStatus.REPORTED)
            assertThat(incident.reportedByUserId).isEqualTo(userId)
            assertThat(incident.odometerKm).isEqualTo(45000)
            assertThat(incident.latitude).isEqualTo(-6.2)
            assertThat(incident.longitude).isEqualTo(106.8)

            coVerify(exactly = 1) { repository.saveIncident(any()) }
        }

    @Test
    fun `should return failure when repository throws exception`(): Unit =
        runBlocking {
            // Arrange
            val exception = RuntimeException("Database error")
            coEvery { repository.saveIncident(any()) } throws exception

            // Act
            val result =
                useCase(
                    vehiclePlate = plate,
                    title = title,
                    description = description,
                    severity = severity,
                    reportedByUserId = userId,
                )

            // Assert
            assertThat(result.isFailure).isTrue()
            assertThat(result.exceptionOrNull()).isEqualTo(exception)
        }
}
