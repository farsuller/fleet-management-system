package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GetVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = GetVehicleUseCase(repository)

    @Test
    fun shouldReturnVehicle_WhenIdExists() = runBlocking {
        // Arrange
        val vehicle = sampleVehicle()
        coEvery { repository.findById(VehicleId("veh-001")) } returns vehicle

        // Act
        val result = useCase.execute("veh-001")

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.vin).isEqualTo("1HGBH41JXMN109186")
    }

    @Test
    fun shouldReturnNull_WhenVehicleNotFound() = runBlocking {
        // Arrange
        coEvery { repository.findById(VehicleId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown")

        // Assert
        assertThat(result).isNull()
    }

    private fun sampleVehicle() = Vehicle(
        id = VehicleId("veh-001"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        mileageKm = 5000,
        state = VehicleState.AVAILABLE
    )
}
