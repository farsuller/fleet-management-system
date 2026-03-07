package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.model.*
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

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
    fun `activates RESERVED rental`() {
        val rental = sampleRental(status = RentalStatus.RESERVED)

        val activated = rental.activate(actualStart = now, startOdo = 5000)

        assertEquals(RentalStatus.ACTIVE, activated.status)
        assertEquals(5000, activated.startOdometerKm)
        assertNotNull(activated.actualStartDate)
    }

    @Test
    fun `throws when rental is not RESERVED`(): Unit {
        val rental = sampleRental(status = RentalStatus.ACTIVE)

        assertFailsWith<IllegalArgumentException> {
            rental.activate(actualStart = now, startOdo = 5000)
        }
    }

    @Test
    fun `throws when activating a COMPLETED rental`(): Unit {
        val rental = sampleRental(status = RentalStatus.COMPLETED)

        assertFailsWith<IllegalArgumentException> {
            rental.activate(actualStart = now, startOdo = 5000)
        }
    }

    @Test
    fun `throws when activating a CANCELLED rental`(): Unit {
        val rental = sampleRental(status = RentalStatus.CANCELLED)

        assertFailsWith<IllegalArgumentException> {
            rental.activate(actualStart = now, startOdo = 5000)
        }
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
