package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * ActivateRentalUseCase wraps execution in `newSuspendedTransaction(Dispatchers.IO)`, so
 * calling useCase.execute() in unit tests requires a live DB connection.
 *
 * Following the tracking module test pattern, these tests exercise the same business
 * rules directly via the Rental domain model's activate() method — no DB needed.
 */
class ActivateRentalUseCaseTest {

    private val now = Instant.now()

    // --- Business rule: rental.activate() requires RESERVED status ---

    @Test
    fun shouldActivateRental_WhenStatusIsReserved() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.RESERVED)

        // Act
        val activated = rental.activate(actualStart = now, startOdo = 5000)

        // Assert
        assertThat(activated.status).isEqualTo(RentalStatus.ACTIVE)
        assertThat(activated.startOdometerKm).isEqualTo(5000)
        assertThat(activated.actualStartDate).isNotNull()
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRentalIsNotReserved() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.ACTIVE)

        // Act / Assert
        assertThatThrownBy {
            rental.activate(actualStart = now, startOdo = 5000)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRentalIsCompleted() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.COMPLETED)

        // Act / Assert
        assertThatThrownBy {
            rental.activate(actualStart = now, startOdo = 5000)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenRentalIsCancelled() {
        // Arrange
        val rental = sampleRental(status = RentalStatus.CANCELLED)

        // Act / Assert
        assertThatThrownBy {
            rental.activate(actualStart = now, startOdo = 5000)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sampleRental(status: RentalStatus) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = VehicleId("veh-001"),
        status = status,
        startDate = now,
        endDate = now.plus(7, ChronoUnit.DAYS),
        dailyRateAmount = 250000,
        totalAmount = 250000 * 7
    )
}
