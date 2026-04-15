package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.DriverShift
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class EndShiftUseCaseTest {
    private val repository = mockk<DriverRepository>()
    private val useCase = EndShiftUseCase(repository)

    @Test
    fun shouldReturnEndedShift_WhenDriverHasActiveShift() =
        runBlocking {
            // Arrange
            val endedShift = sampleShift(endedAt = Instant.now())
            coEvery { repository.endShift("driver-001", "All good") } returns endedShift

            // Act
            val result = useCase.execute("driver-001", "All good")

            // Assert
            assertThat(result).isEqualTo(endedShift)
            assertThat(result.endedAt).isNotNull()
        }

    @Test
    fun shouldThrowIllegalState_WhenDriverHasNoActiveShift() {
        // Arrange
        coEvery { repository.endShift("driver-002", null) } returns null

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("driver-002", null) } }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("No active shift found for driver: driver-002")
    }

    private fun sampleShift(endedAt: Instant? = null) =
        DriverShift(
            id = UUID.randomUUID(),
            driverId = UUID.randomUUID(),
            vehicleId = UUID.randomUUID(),
            startedAt = Instant.now().minusSeconds(3600),
            endedAt = endedAt,
        )
}
