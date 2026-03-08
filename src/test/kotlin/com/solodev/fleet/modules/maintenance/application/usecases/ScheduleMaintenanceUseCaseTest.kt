package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ScheduleMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ScheduleMaintenanceUseCase(repository)

    private val validRequest = MaintenanceRequest(
        vehicleId = "veh-001",
        jobType = "ROUTINE",
        description = "Regular oil change service",
        scheduledDate = "2026-03-10T00:00:00Z"
    )

    @Test
    fun shouldScheduleMaintenanceJob_WhenDataIsValid() = runBlocking {
        // Arrange
        val savedJob = slot<MaintenanceJob>()
        coEvery { repository.saveJob(capture(savedJob)) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.vehicleId).isEqualTo(VehicleId("veh-001"))
        assertThat(result.status).isEqualTo(MaintenanceStatus.SCHEDULED)
        assertThat(savedJob.captured.vehicleId).isEqualTo(VehicleId("veh-001"))
    }

    @Test
    fun shouldThrowIllegalArgument_WhenJobTypeIsUnknown() {
        // Act / Assert
        assertThatThrownBy {
            runBlocking {
                useCase.execute(
                    MaintenanceRequest(
                        vehicleId = "veh-001",
                        jobType = "UNKNOWN_TYPE",
                        description = "Some maintenance description here",
                        scheduledDate = "2026-03-10T00:00:00Z"
                    )
                )
            }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldAutoGenerateJobNumber_WhenJobIsScheduled() = runBlocking {
        // Arrange
        coEvery { repository.saveJob(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.jobNumber).startsWith("MAINT-")
    }
}
