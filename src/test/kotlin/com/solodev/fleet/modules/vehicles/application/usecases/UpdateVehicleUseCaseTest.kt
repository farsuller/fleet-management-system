package com.solodev.fleet.modules.vehicles.application.usecases

import com.solodev.fleet.modules.vehicles.application.dto.VehicleUpdateRequest
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UpdateVehicleUseCaseTest {

    private val repository = mockk<VehicleRepository>()
    private val useCase = UpdateVehicleUseCase(repository)

    private val existingVehicle = Vehicle(
        id = VehicleId("vehicle-1"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        color = "White",
        dailyRateAmount = 150000,
        state = VehicleState.AVAILABLE
    )

    @Test
    fun shouldUpdateLicensePlate_WhenVehicleExists() = runBlocking {
        // Arrange
        val request = VehicleUpdateRequest(licensePlate = "XYZ-9999")
        val savedSlot = slot<Vehicle>()
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns existingVehicle
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", request)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.licensePlate).isEqualTo("XYZ-9999")
        assertThat(savedSlot.captured.licensePlate).isEqualTo("XYZ-9999")
        assertThat(savedSlot.captured.vin).isEqualTo("1HGBH41JXMN109186")
    }

    @Test
    fun shouldConvertDailyRateToCents_WhenDailyRateProvided() = runBlocking {
        // Arrange
        val request = VehicleUpdateRequest(dailyRate = 2000.0)
        val savedSlot = slot<Vehicle>()
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns existingVehicle
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", request)

        // Assert
        assertThat(result).isNotNull()
        assertThat(savedSlot.captured.dailyRateAmount).isEqualTo(200000) // 2000.0 * 100
    }

    @Test
    fun shouldPreserveUnchangedFields_WhenPartialUpdateProvided() = runBlocking {
        // Arrange
        val request = VehicleUpdateRequest(color = "Red")
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns existingVehicle
        coEvery { repository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", request)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.licensePlate).isEqualTo("ABC-1234")   // unchanged
        assertThat(result.make).isEqualTo("Toyota")               // unchanged
        assertThat(result.model).isEqualTo("Corolla")             // unchanged
        assertThat(result.year).isEqualTo(2023)                   // unchanged
        assertThat(result.dailyRateAmount).isEqualTo(150000)       // unchanged
        assertThat(result.color).isEqualTo("Red")
    }

    @Test
    fun shouldUpdateMakeModelAndYear_WhenProvided() = runBlocking {
        // Arrange
        val request = VehicleUpdateRequest(make = "Honda", model = "Civic", year = 2024)
        val savedSlot = slot<Vehicle>()
        coEvery { repository.findById(VehicleId("vehicle-1")) } returns existingVehicle
        coEvery { repository.save(capture(savedSlot)) } returnsArgument 0

        // Act
        val result = useCase.execute("vehicle-1", request)

        // Assert
        assertThat(result).isNotNull()
        assertThat(result!!.make).isEqualTo("Honda")
        assertThat(result.model).isEqualTo("Civic")
        assertThat(result.year).isEqualTo(2024)
        assertThat(savedSlot.captured.make).isEqualTo("Honda")
    }

    @Test
    fun shouldReturnNull_WhenVehicleNotFound() = runBlocking {
        // Arrange
        coEvery { repository.findById(VehicleId("unknown")) } returns null

        // Act
        val result = useCase.execute("unknown", VehicleUpdateRequest(licensePlate = "NEW-0000"))

        // Assert
        assertThat(result).isNull()
        coVerify(exactly = 0) { repository.save(any()) }
    }
}
