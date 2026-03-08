package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CompleteRentalUseCaseTest {

    private val rentalRepository = mockk<RentalRepository>()
    private val vehicleRepository = mockk<VehicleRepository>()
    private val useCase = CompleteRentalUseCase(rentalRepository, vehicleRepository)

    @Test
    fun shouldCompleteRentalAndReturnVehicleToAvailable_WhenRentalIsActive() = runBlocking {
        // Arrange
        val rental = sampleRental(status = RentalStatus.ACTIVE, startOdometerKm = 5000)
        val vehicle = sampleVehicle(mileageKm = 5000)
        val savedVehicle = slot<Vehicle>()
        coEvery { rentalRepository.findById(RentalId("rental-001")) } returns rental
        coEvery { vehicleRepository.findById(VehicleId("veh-001")) } returns vehicle
        coEvery { vehicleRepository.save(capture(savedVehicle)) } returnsArgument 0
        coEvery { rentalRepository.save(any()) } returnsArgument 0

        // Act
        val result = useCase.execute("rental-001", finalMileage = 5150)

        // Assert
        assertThat(result.status).isEqualTo(RentalStatus.COMPLETED)
        assertThat(result.endOdometerKm).isEqualTo(5150)
        assertThat(savedVehicle.captured.state).isEqualTo(VehicleState.AVAILABLE)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRentalIsNotActive() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.RESERVED)
        coEvery { rentalRepository.findById(RentalId("rental-001")) } returns rental

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("rental-001", finalMileage = 5150) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenFinalMileageLessThanStart() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.ACTIVE, startOdometerKm = 5000)
        val vehicle = sampleVehicle(mileageKm = 5000)
        coEvery { rentalRepository.findById(RentalId("rental-001")) } returns rental
        coEvery { vehicleRepository.findById(VehicleId("veh-001")) } returns vehicle

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("rental-001", finalMileage = 4999) } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sampleRental(status: RentalStatus, startOdometerKm: Int? = null) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = VehicleId("veh-001"),
        status = status,
        startDate = Instant.now(),
        endDate = Instant.now().plus(7, ChronoUnit.DAYS),
        dailyRateAmount = 1000,
        totalAmount = 7000,
        startOdometerKm = startOdometerKm
    )

    private fun sampleVehicle(mileageKm: Int = 0) = Vehicle(
        id = VehicleId("veh-001"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        state = VehicleState.RENTED,
        mileageKm = mileageKm
    )
}
