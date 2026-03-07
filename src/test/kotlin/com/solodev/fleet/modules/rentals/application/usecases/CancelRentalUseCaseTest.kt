package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class CancelRentalUseCaseTest {

    private val repository = mockk<RentalRepository>()
    private val useCase = CancelRentalUseCase(repository)

    @Test
    fun `cancels RESERVED rental`() = runBlocking {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        coEvery { repository.findById(any()) } returns rental
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute("rental-001")

        assertEquals(RentalStatus.CANCELLED, result.status)
        coVerify { repository.save(any()) }
    }

    @Test
    fun `cancels ACTIVE rental`() = runBlocking {
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        coEvery { repository.findById(any()) } returns rental
        coEvery { repository.save(any()) } returnsArgument 0

        val result = useCase.execute("rental-001")

        assertEquals(RentalStatus.CANCELLED, result.status)
    }

    @Test
    fun `throws when trying to cancel COMPLETED rental`(): Unit = runBlocking {
        val rental = sampleRental(status = RentalStatus.COMPLETED)
        coEvery { repository.findById(any()) } returns rental

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("rental-001")
        }
    }

    @Test
    fun `throws when rental not found`(): Unit = runBlocking {
        coEvery { repository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-id")
        }
    }

    private fun sampleRental(status: RentalStatus) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = VehicleId("veh-001"),
        status = status,
        startDate = Instant.now(),
        endDate = Instant.now().plus(7, ChronoUnit.DAYS),
        dailyRateAmount = 1000,
        totalAmount = 7000
    )
}
