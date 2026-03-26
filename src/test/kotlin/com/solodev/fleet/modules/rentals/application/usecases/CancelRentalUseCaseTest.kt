package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class CancelRentalUseCaseTest {

    private val repository = mockk<RentalRepository>()
    private val useCase = CancelRentalUseCase(repository)

    @Test
    fun shouldCancelRental_WhenStatusIsReserved() = runBlocking {
        // Arrange
        val rental = sampleRental(status = RentalStatus.RESERVED)
        val rentalId = RentalId("rental-001")
        val savedRental = slot<Rental>()
        coEvery { repository.findById(rentalId) } returns rental
        coEvery { repository.save(capture(savedRental)) } returnsArgument 0
        coEvery { repository.findByIdWithDetails(rentalId) } answers {
            RentalWithDetails(savedRental.captured, "XYZ-123", "Toyota", "Corolla", "John Doe")
        }

        // Act
        val result = useCase.execute("rental-001")

        // Assert
        assertThat(result.rental.status).isEqualTo(RentalStatus.CANCELLED)
        assertThat(savedRental.captured.status).isEqualTo(RentalStatus.CANCELLED)
    }

    @Test
    fun shouldCancelRental_WhenStatusIsActive() = runBlocking {
        // Arrange
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        val rentalId = RentalId("rental-001")
        coEvery { repository.findById(rentalId) } returns rental
        coEvery { repository.save(any()) } returnsArgument 0
        coEvery { repository.findByIdWithDetails(rentalId) } answers {
            RentalWithDetails(rental.copy(status = RentalStatus.CANCELLED), "XYZ-123", "Toyota", "Corolla", "John Doe")
        }

        // Act
        val result = useCase.execute("rental-001")

        // Assert
        assertThat(result.rental.status).isEqualTo(RentalStatus.CANCELLED)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenCancellingCompletedRental() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.COMPLETED)
        coEvery { repository.findById(RentalId("rental-001")) } returns rental

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("rental-001") } }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRentalNotFound() {
        // Arrange
        coEvery { repository.findById(RentalId("unknown-id")) } returns null

        // Act / Assert
        assertThatThrownBy { runBlocking { useCase.execute("unknown-id") } }
            .isInstanceOf(IllegalArgumentException::class.java)
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
