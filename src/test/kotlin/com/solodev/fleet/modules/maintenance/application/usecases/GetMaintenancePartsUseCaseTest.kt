package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePart
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GetMaintenancePartsUseCaseTest {
    private val repository = mockk<MaintenanceRepository>()
    private val useCase = GetMaintenancePartsUseCase(repository)

    private val jobIdStr = "job-001"
    private val jobId = MaintenanceJobId(jobIdStr)

    private fun part(partName: String) =
        MaintenancePart(
            id = UUID.randomUUID(),
            jobId = jobId,
            partNumber = "PN-${UUID.randomUUID()}",
            partName = partName,
            quantity = 2,
            unitCost = 500,
            currencyCode = "PHP",
        )

    @Test
    fun `should return parts for a given job id`() =
        runBlocking {
            val parts = listOf(part("Oil Filter"), part("Air Filter"))
            coEvery { repository.findPartsByJobId(jobId) } returns parts

            val result = useCase.execute(jobIdStr)

            assertThat(result).hasSize(2)
            assertThat(result.map { it.partName }).containsExactly("Oil Filter", "Air Filter")
            coVerify(exactly = 1) { repository.findPartsByJobId(jobId) }
        }

    @Test
    fun `should return empty list when job has no parts`() =
        runBlocking {
            coEvery { repository.findPartsByJobId(jobId) } returns emptyList()

            val result = useCase.execute(jobIdStr)

            assertThat(result).isEmpty()
            coVerify(exactly = 1) { repository.findPartsByJobId(jobId) }
        }
}
