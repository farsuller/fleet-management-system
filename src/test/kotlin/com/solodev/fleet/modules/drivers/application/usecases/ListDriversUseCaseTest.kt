package com.solodev.fleet.modules.drivers.application.usecases

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.repository.DriverRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class ListDriversUseCaseTest {
    private val repository = mockk<DriverRepository>()
    private val useCase = ListDriversUseCase(repository)

    @Test
    fun shouldReturnAllDrivers_WhenDriversExist() =
        runBlocking {
            // Arrange
            val drivers = listOf(sampleDriver("driver-001"), sampleDriver("driver-002"))
            coEvery { repository.findAll() } returns drivers

            // Act
            val result = useCase.execute()

            // Assert
            assertThat(result).hasSize(2)
            assertThat(result.map { it.id.value }).containsExactlyInAnyOrder("driver-001", "driver-002")
        }

    @Test
    fun shouldReturnEmptyList_WhenNoDriversExist() =
        runBlocking {
            // Arrange
            coEvery { repository.findAll() } returns emptyList()

            // Act
            val result = useCase.execute()

            // Assert
            assertThat(result).isEmpty()
        }

    private fun sampleDriver(id: String) =
        Driver(
            id = DriverId(id),
            firstName = "Juan",
            lastName = "dela Cruz",
            email = "juan.$id@fleet.ph",
            phone = "+63912345678",
            licenseNumber = "LN-$id",
            licenseExpiry = Instant.now().plusSeconds(86400L * 365),
        )
}
