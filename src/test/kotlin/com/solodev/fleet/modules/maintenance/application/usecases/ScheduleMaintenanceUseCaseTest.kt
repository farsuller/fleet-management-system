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
        type = MaintenanceJobType.PREVENTIVE,
        priority = MaintenancePriority.NORMAL,
        description = "Regular oil change service",
        scheduledDate = System.currentTimeMillis(),
        estimatedCostPhp = 1000L
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

    // Removed shouldThrowIllegalArgument_WhenJobTypeIsUnknown since type is now an Enum type.
    // Serialization handles invalid enum strings.

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
