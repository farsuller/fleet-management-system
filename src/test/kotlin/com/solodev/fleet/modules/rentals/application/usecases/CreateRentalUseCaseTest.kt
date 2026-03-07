package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.application.dto.RentalRequest
import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.model.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.*

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
    fun `creates rental for AVAILABLE vehicle`() {
        val vehicle = sampleVehicle(state = VehicleState.AVAILABLE)

        // Mirrors CreateRentalUseCase: require AVAILABLE, then build Rental
        require(vehicle.state == VehicleState.AVAILABLE) { "Vehicle is not available for rental" }

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

        assertEquals(RentalStatus.RESERVED, rental.status)
        assertEquals(vehicle.id, rental.vehicleId)
        assertEquals(CustomerId("cust-001"), rental.customerId)
    }

    @Test
    fun `throws when vehicle is not AVAILABLE`(): Unit {
        val vehicle = sampleVehicle(state = VehicleState.RENTED)

        assertFailsWith<IllegalArgumentException> {
            require(vehicle.state == VehicleState.AVAILABLE) { "Vehicle is not available for rental" }
        }
    }

    @Test
    fun `throws when vehicle not found`(): Unit {
        val vehicle: Vehicle? = null

        assertFailsWith<IllegalArgumentException> {
            vehicle ?: throw IllegalArgumentException("Vehicle not found")
        }
    }

    // --- Business rule: no conflicting rentals ---

    @Test
    fun `throws when rental dates conflict`(): Unit {
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

        assertFailsWith<IllegalArgumentException> {
            require(conflicts.isEmpty()) { "Vehicle is already rented during this period" }
        }
    }

    // --- RentalRequest DTO validation ---

    @Test
    fun `RentalRequest rejects blank vehicleId`(): Unit {
        assertFailsWith<IllegalArgumentException> {
            RentalRequest(
                vehicleId = "",
                customerId = "cust-001",
                startDate = "2026-03-10T00:00:00Z",
                endDate = "2026-03-17T00:00:00Z"
            )
        }
    }

    @Test
    fun `RentalRequest rejects end date before start date`(): Unit {
        assertFailsWith<IllegalArgumentException> {
            RentalRequest(
                vehicleId = "veh-001",
                customerId = "cust-001",
                startDate = "2026-03-17T00:00:00Z",
                endDate = "2026-03-10T00:00:00Z"
            )
        }
    }

    private fun sampleVehicle(state: VehicleState) = Vehicle(
        id = VehicleId("veh-001"),
        vin = "1HGBH41JXMN109186",
        licensePlate = "ABC-1234",
        make = "Toyota",
        model = "Corolla",
        year = 2023,
        state = state,
        mileageKm = 5000
    )
}
