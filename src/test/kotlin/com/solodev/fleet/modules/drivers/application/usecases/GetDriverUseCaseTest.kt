package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GetDriverUseCaseTest {

    private val repository = mockk<DriverRepository>()
    private val useCase = GetDriverUseCase(repository)

    @Test
    fun shouldReturnDriver_WhenIdExists() = runBlocking {
        val driver = sampleDriver()
        coEvery { repository.findById(DriverId("driver-001")) } returns driver

        val result = useCase.execute("driver-001")

        assertThat(result).isNotNull()
        assertThat(result!!.id.value).isEqualTo("driver-001")
    }

    @Test
    fun shouldReturnNull_WhenIdNotFound() = runBlocking {
        coEvery { repository.findById(DriverId("unknown")) } returns null

        val result = useCase.execute("unknown")

        assertThat(result).isNull()
    }

    private fun sampleDriver() = Driver(
        id            = DriverId("driver-001"),
        firstName     = "Pedro",
        lastName      = "Reyes",
        email         = "pedro@fleet.ph",
        phone         = "+63917000001",
        licenseNumber = "LN-0001",
        licenseExpiry = Instant.parse("2030-01-01T00:00:00Z"),
    )
}
