package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.DriverShift
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GetActiveShiftUseCaseTest {
    private val repository = mockk<DriverRepository>()
    private val useCase = GetActiveShiftUseCase(repository)

    @Test
    fun shouldReturnActiveShift_WhenDriverHasAnActiveShift() =
        runBlocking {
            // Arrange
            val shift = sampleShift()
            coEvery { repository.findActiveShift("driver-001") } returns shift

            // Act
            val result = useCase.execute("driver-001")

            // Assert
            assertThat(result).isEqualTo(shift)
            assertThat(result?.endedAt).isNull()
        }

    @Test
    fun shouldReturnNull_WhenDriverHasNoActiveShift() =
        runBlocking {
            // Arrange
            coEvery { repository.findActiveShift("driver-002") } returns null

            // Act
            val result = useCase.execute("driver-002")

            // Assert
            assertThat(result).isNull()
        }

    private fun sampleShift() =
        DriverShift(
            id = UUID.randomUUID(),
            driverId = UUID.randomUUID(),
            vehicleId = UUID.randomUUID(),
            startedAt = Instant.now().minusSeconds(3600),
            endedAt = null,
        )
}
