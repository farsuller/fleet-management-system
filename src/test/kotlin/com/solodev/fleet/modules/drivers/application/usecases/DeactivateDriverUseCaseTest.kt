package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class DeactivateDriverUseCaseTest {

    private val repository = mockk<DriverRepository>()
    private val useCase = DeactivateDriverUseCase(repository)

    @Test
    fun shouldDeactivateActiveDriver() = runBlocking {
        val active = sampleDriver(isActive = true)
        val deactivated = active.copy(isActive = false)
        coEvery { repository.findById(DriverId("driver-001")) } returns active
        coEvery { repository.save(deactivated) } returns deactivated

        val result = useCase.execute("driver-001")

        assertThat(result).isNotNull()
        assertThat(result!!.isActive).isFalse()
        coVerify { repository.save(deactivated) }
    }

    @Test
    fun shouldReactivateInactiveDriver() = runBlocking {
        val inactive = sampleDriver(isActive = false)
        val reactivated = inactive.copy(isActive = true)
        coEvery { repository.findById(DriverId("driver-001")) } returns inactive
        coEvery { repository.save(reactivated) } returns reactivated

        val result = useCase.execute("driver-001")

        assertThat(result).isNotNull()
        assertThat(result!!.isActive).isTrue()
        coVerify { repository.save(reactivated) }
    }

    @Test
    fun shouldReturnNull_WhenDriverNotFound() = runBlocking {
        coEvery { repository.findById(DriverId("unknown")) } returns null

        val result = useCase.execute("unknown")

        assertThat(result).isNull()
        coVerify(exactly = 0) { repository.save(any()) }
    }

    private fun sampleDriver(isActive: Boolean = true) = Driver(
        id            = DriverId("driver-001"),
        firstName     = "Pedro",
        lastName      = "Reyes",
        email         = "pedro@fleet.ph",
        phone         = "+63917000001",
        licenseNumber = "LN-0001",
        licenseExpiry = Instant.parse("2030-01-01T00:00:00Z"),
        isActive      = isActive,
    )
}
