package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UpdateVehicleStateUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = UpdateVehicleStateUseCase(repository)

    private val availableVehicle = Vehicle(
        id = VehicleId("vehicle-1"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        state = VehicleState.AVAILABLE
    )

    @Test
    fun shouldUpdateState_WhenVehicleExists() = runBlocking {
        // Arrange
        val savedSlot = slot<Vehicle>()
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns availableVehicle
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", "RENTED")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.state).isEqualTo(VehicleState.RENTED)
        assertThat(savedSlot.captured.state).isEqualTo(VehicleState.RENTED)
    }

    @Test
    fun shouldUpdateToMaintenance_WhenNewStateIsMaintenance() = runBlocking {
        // Arrange
        val savedSlot = slot<Vehicle>()
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns availableVehicle
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", "MAINTENANCE")

        // Assert
        assertThat(result).isNotNull()
        assertThat(savedSlot.captured.state).isEqualTo(VehicleState.MAINTENANCE)
    }

    @Test
    fun shouldReturnNull_WhenVehicleNotFound() = runBlocking {
        // Arrange
        coEvery { repository.findById(VehicleId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown", "RENTED")

        // Assert
        assertThat(result).isNull()
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenStateIsInvalid() {
        // Arrange
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns availableVehicle

        // Act + Assert
        assertThatThrownBy { runBlocking { useCase.execute("vehicle-1", "FLYING") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
