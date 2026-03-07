package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Test
import kotlin.test.*

class CompleteRentalUseCaseTest {

    private val rentalRepository = mockk<RentalRepository>()
    private val vehicleRepository = mockk<VehicleRepository>()
    private val useCase = CompleteRentalUseCase(rentalRepository, vehicleRepository)

    @Test
    fun `completes ACTIVE rental and returns vehicle to AVAILABLE`() = runBlocking {
        val rental = sampleRental(status = RentalStatus.ACTIVE, startOdometerKm = 5000)
        val vehicle = sampleVehicle(mileageKm = 5000)

        coEvery { rentalRepository.findById(any()) } returns rental
        coEvery { vehicleRepository.findById(any()) } returns vehicle
        coEvery { vehicleRepository.save(any()) } returnsArgument 0
        coEvery { rentalRepository.save(any()) } returnsArgument 0

        val result = useCase.execute("rental-001", finalMileage = 5150)

        assertEquals(RentalStatus.COMPLETED, result.status)
        assertEquals(5150, result.endOdometerKm)
        coVerify { vehicleRepository.save(any()) }
    }

    @Test
    fun `throws when rental is not ACTIVE`(): Unit = runBlocking {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        coEvery { rentalRepository.findById(any()) } returns rental

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("rental-001", finalMileage = 5150)
        }
    }

    @Test
    fun `throws when final mileage is less than start mileage`(): Unit = runBlocking {
        val rental = sampleRental(status = RentalStatus.ACTIVE, startOdometerKm = 5000)
        val vehicle = sampleVehicle(mileageKm = 5000)
        coEvery { rentalRepository.findById(any()) } returns rental
        coEvery { vehicleRepository.findById(any()) } returns vehicle

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("rental-001", finalMileage = 4999)
        }
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
