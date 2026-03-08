package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DeleteVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = DeleteVehicleUseCase(repository)

    @Test
    fun shouldDeleteVehicleAndReturnTrue_WhenIdExists() = runBlocking {
        // Arrange
        coEvery { repository.deleteById(VehicleId("veh-001")) } returns true

        // Act
        val result = useCase.execute("veh-001")

        // Assert
        assertThat(result).isTrue()
        coVerify { repository.deleteById(VehicleId("veh-001")) }
    }

    @Test
    fun shouldReturnFalse_WhenVehicleNotFound() = runBlocking {
        // Arrange
        coEvery { repository.deleteById(VehicleId("unknown")) } returns false

        // Act
        val result = useCase.execute("unknown")

        // Assert
        assertThat(result).isFalse()
    }
}
