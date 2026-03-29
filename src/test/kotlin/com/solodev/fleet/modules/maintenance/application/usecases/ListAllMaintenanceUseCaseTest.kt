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

class ListAllMaintenanceUseCaseTest {

    private val repository = mockk<MaintenanceRepository>()
    private val useCase = ListAllMaintenanceUseCase(repository)

    private val job1 = MaintenanceJob(
        id = MaintenanceJobId("job-1"),
        jobNumber = "MNT-001",
        vehicleId = VehicleId("vehicle-1"),
        status = MaintenanceStatus.SCHEDULED,
        jobType = MaintenanceJobType.PREVENTIVE,
        description = "Oil change",
        scheduledDate = Instant.parse("2026-04-01T00:00:00Z")
    )
    private val job2 = MaintenanceJob(
        id = MaintenanceJobId("job-2"),
        jobNumber = "MNT-002",
        vehicleId = VehicleId("vehicle-2"),
        status = MaintenanceStatus.IN_PROGRESS,
        jobType = MaintenanceJobType.CORRECTIVE,
        description = "Brake pad replacement",
        scheduledDate = Instant.parse("2026-03-15T08:00:00Z")
    )

    @Test
    fun shouldReturnAllJobs_WhenNoFilterProvided() = runBlocking {
        // Arrange
        coEvery { repository.findAll() } returns listOf(job1, job2)

        // Act
        val result = useCase.execute()

        // Assert
        assertThat(result).hasSize(2)
        assertThat(result.map { it.jobNumber }).containsExactlyInAnyOrder("MNT-001", "MNT-002")
        coVerify(exactly = 1) { repository.findAll() }
        coVerify(exactly = 0) { repository.findByStatus(any()) }
    }

    @Test
    fun shouldReturnFilteredJobs_WhenStatusProvided() = runBlocking {
        // Arrange
        coEvery { repository.findByStatus(MaintenanceStatus.SCHEDULED) } returns listOf(job1)

        // Act
        val result = useCase.execute(status = MaintenanceStatus.SCHEDULED)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].status).isEqualTo(MaintenanceStatus.SCHEDULED)
        assertThat(result[0].jobNumber).isEqualTo("MNT-001")
        coVerify(exactly = 1) { repository.findByStatus(MaintenanceStatus.SCHEDULED) }
        coVerify(exactly = 0) { repository.findAll() }
    }

    @Test
    fun shouldReturnEmptyList_WhenNoJobsExist() = runBlocking {
        // Arrange
        coEvery { repository.findAll() } returns emptyList()

        // Act
        val result = useCase.execute()

        // Assert
        assertThat(result).isEmpty()
        coVerify(exactly = 1) { repository.findAll() }
    }
}
