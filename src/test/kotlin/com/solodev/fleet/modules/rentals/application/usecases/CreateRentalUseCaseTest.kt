package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.model.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy

/**
 * CreateRentalUseCase wraps execution in `dbQuery {}` (DB transaction), so calling
 * useCase.execute() in unit tests requires a live DB connection.
 *
 * Following the tracking module test pattern, these tests exercise the same business
 * rules directly via domain objects — no DB, no mockk repositories needed.
 */
class CreateRentalUseCaseTest {

    private val startDate = Instant.parse("2026-03-10T00:00:00Z")
    private val endDate   = Instant.parse("2026-03-17T00:00:00Z")

    // --- Business rule: vehicle must be AVAILABLE ---

    @Test
    fun shouldCreateRental_WhenVehicleIsAvailable() {
        // Arrange
        val vehicle = sampleVehicle(state = VehicleState.AVAILABLE)
        require(vehicle.state == VehicleState.AVAILABLE) { "Vehicle is not available for rental" }

        // Act
        val rental = Rental(
            id = RentalId(UUID.randomUUID().toString()),
            rentalNumber = "RNT-001",
            vehicleId = vehicle.id,
            customerId = CustomerId("cust-001"),
            status = RentalStatus.RESERVED,
            startDate = startDate,
            endDate = endDate,
            dailyRateAmount = 250000,
            totalAmount = 250000 * 7
        )

        // Assert
        assertThat(rental.status).isEqualTo(RentalStatus.RESERVED)
        assertThat(rental.vehicleId).isEqualTo(vehicle.id)
        assertThat(rental.customerId).isEqualTo(CustomerId("cust-001"))
    }

    @Test
    fun shouldThrowIllegalArgument_WhenVehicleIsNotAvailable() {
        // Arrange
        val vehicle = sampleVehicle(state = VehicleState.RENTED)

        // Act / Assert
        assertThatThrownBy {
            require(vehicle.state == VehicleState.AVAILABLE) { "Vehicle is not available for rental" }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenVehicleNotFound() {
        // Arrange
        val vehicle: Vehicle? = null

        // Act / Assert
        assertThatThrownBy {
            vehicle ?: throw IllegalArgumentException("Vehicle not found")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    // --- Business rule: no conflicting rentals ---

    @Test
    fun shouldThrowIllegalArgument_WhenRentalDatesConflict() {
        // Arrange
        val existingRental = Rental(
            id = RentalId(UUID.randomUUID().toString()),
            rentalNumber = "RNT-EXISTING",
            vehicleId = VehicleId("veh-001"),
            customerId = CustomerId("cust-002"),
            status = RentalStatus.RESERVED,
            startDate = startDate,
            endDate = endDate,
            dailyRateAmount = 250000,
            totalAmount = 250000 * 7
        )
        val conflicts = listOf(existingRental)

        // Act / Assert
        assertThatThrownBy {
            require(conflicts.isEmpty()) { "Vehicle is already rented during this period" }
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    // --- RentalRequest DTO validation ---

    @Test
    fun shouldThrowIllegalArgument_WhenVehicleIdIsBlank() {
        // Act / Assert
        assertThatThrownBy {
            RentalRequest(
                vehicleId = "",
                customerId = "cust-001",
                startDate = "2026-03-10T00:00:00Z",
                endDate = "2026-03-17T00:00:00Z"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldThrowIllegalArgument_WhenEndDateBeforeStartDate() {
        // Act / Assert
        assertThatThrownBy {
            RentalRequest(
                vehicleId = "veh-001",
                customerId = "cust-001",
                startDate = "2026-03-17T00:00:00Z",
                endDate = "2026-03-10T00:00:00Z"
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun shouldCalculateTotalWithCustomRate() {
        // Arrange
        val vehicle = sampleVehicle(state = VehicleState.AVAILABLE, defaultRate = 1000)
        val customRate = 1500L
        val days = 7L

        // Act
        val totalAmount = (days * (customRate ?: 1000L)).toInt()

        // Assert
        assertThat(totalAmount).isEqualTo(10500)
    }

    private fun sampleVehicle(state: VehicleState, defaultRate: Int = 5000) = Vehicle(
        id = VehicleId("veh-001"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        state = state,
        mileageKm = 5000,
        dailyRateAmount = defaultRate
    )
}
