package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.model.VehicleType
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DeleteVehicleUseCaseTest {
    private val repository = mockk<VehicleRepository>()
    private val useCase = DeleteVehicleUseCase(repository)

    @Test
    fun shouldDeleteVehicleAndReturnTrue_WhenIdExists(): Unit =
        runBlocking {
            // Arrange
            val id = "veh-001"
            val vehicle = createSampleVehicle(id, VehicleState.AVAILABLE)
            coEvery { repository.findById(VehicleId(id)) } returns vehicle
            coEvery { repository.deleteById(VehicleId(id)) } returns true

            // Act
            val result = useCase.execute(id)

            // Assert
            assertThat(result).isTrue()
            coVerify { repository.deleteById(VehicleId(id)) }
        }

    @Test
    fun shouldReturnFalse_WhenVehicleNotFound(): Unit =
        runBlocking {
            // Arrange
            coEvery { repository.findById(VehicleId("unknown")) } returns null

            // Act
            val result = useCase.execute("unknown")

            // Assert
            assertThat(result).isFalse()
        }

    @Test
    fun shouldThrowException_WhenVehicleIsRented(): Unit =
        runBlocking {
            // Arrange
            val id = "veh-rented"
            val vehicle = createSampleVehicle(id, VehicleState.RENTED)
            coEvery { repository.findById(VehicleId(id)) } returns vehicle

            // Act & Assert
            assertThrows<IllegalStateException> {
                runBlocking { useCase.execute(id) }
            }
        }

    private fun createSampleVehicle(
        id: String,
        state: VehicleState,
    ): Vehicle =
        Vehicle(
            id = VehicleId(id),
            licensePlate = "PLATE-$id",
            make = "Toyota",
            model = "Corolla",
            year = 2022,
            vehicleType = VehicleType.CAR,
            state = state,
        )
}
