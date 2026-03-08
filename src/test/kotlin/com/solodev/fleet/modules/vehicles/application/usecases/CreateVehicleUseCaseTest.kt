package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.application.dto.VehicleRequest
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CreateVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = CreateVehicleUseCase(repository)

    private val validRequest = VehicleRequest(
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023
    )

    @Test
    fun shouldCreateVehicle_WhenDataIsValid() = runBlocking {
        // Arrange
        val savedVehicle = slot<Vehicle>()
        coEvery { repository.save(capture(savedVehicle)) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.vin).isEqualTo("1HGBH41JXMN109186")
        assertThat(result.licensePlate).isEqualTo("ABC-1234")
        assertThat(result.state).isEqualTo(VehicleState.AVAILABLE)
        assertThat(savedVehicle.captured.vin).isEqualTo("1HGBH41JXMN109186")
    }

    @Test
    fun shouldDefaultToAvailable_WhenVehicleIsCreated() = runBlocking {
        // Arrange
        coEvery { repository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute(validRequest)

        // Assert
        assertThat(result.state).isEqualTo(VehicleState.AVAILABLE)
    }
}
