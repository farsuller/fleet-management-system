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

class RecordOdometerUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = RecordOdometerUseCase(repository)

    private val vehicle = Vehicle(
        id = VehicleId("vehicle-1"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        mileageKm = 10000,
        state = VehicleState.AVAILABLE
    )

    @Test
    fun shouldUpdateMileage_WhenNewReadingIsHigher() = runBlocking {
        // Arrange
        val savedSlot = slot<Vehicle>()
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns vehicle
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", 12000)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.mileageKm).isEqualTo(12000)
        assertThat(savedSlot.captured.mileageKm).isEqualTo(12000)
    }

    @Test
    fun shouldAcceptSameMileage_WhenNewReadingEqualsCurrentMileage() = runBlocking {
        // Arrange
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns vehicle
        coEvery { repository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", 10000)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.mileageKm).isEqualTo(10000)
    }

    @Test
    fun shouldReturnNull_WhenVehicleNotFound() = runBlocking {
        // Arrange
        coEvery { repository.findById(VehicleId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown", 15000)

        // Assert
        assertThat(result).isNull()
        coVerify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun shouldThrowIllegalArgument_WhenNewMileageIsLower() {
        // Arrange
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns vehicle

        // Act + Assert
        assertThatThrownBy { runBlocking { useCase.execute("vehicle-1", 5000) } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cannot be less than current mileage")
    }
}
