package com.solodev.fleet.modules.rentals.domain.model

import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Test
import kotlin.test.*

class RentalTest {

    private val now = Instant.now()
    private val tomorrow = now.plus(1, ChronoUnit.DAYS)
    private val nextWeek = now.plus(7, ChronoUnit.DAYS)

    // --- Invariant: endDate must be after startDate ---

    @Test
    fun `throws when endDate is before startDate`() {
        assertFailsWith<IllegalArgumentException> {
            sampleRental(startDate = tomorrow, endDate = now)
        }
    }

    @Test
    fun `throws when totalAmount is negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleRental(totalAmount = -1000)
        }
    }

    // --- activate() ---

    @Test
    fun `activate transitions RESERVED to ACTIVE`() {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        val activated = rental.activate(actualStart = now, startOdo = 5000)
        assertEquals(RentalStatus.ACTIVE, activated.status)
        assertEquals(5000, activated.startOdometerKm)
    }

    @Test
    fun `activate throws when rental is not RESERVED`() {
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        assertFailsWith<IllegalArgumentException> {
            rental.activate(actualStart = now, startOdo = 5000)
        }
    }

    // --- complete() ---

    @Test
    fun `complete transitions ACTIVE to COMPLETED`() {
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        val completed = rental.complete(actualEnd = tomorrow, endOdo = 5150)
        assertEquals(RentalStatus.COMPLETED, completed.status)
        assertNotNull(completed.actualEndDate)
    }

    @Test
    fun `complete throws when rental is not ACTIVE`() {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        assertFailsWith<IllegalArgumentException> {
            rental.complete(actualEnd = tomorrow, endOdo = 5150)
        }
    }

    // --- cancel() ---

    @Test
    fun `cancel transitions RESERVED to CANCELLED`() {
        val rental = sampleRental(status = RentalStatus.RESERVED)
        val cancelled = rental.cancel()
        assertEquals(RentalStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `cancel transitions ACTIVE to CANCELLED`() {
        val rental = sampleRental(status = RentalStatus.ACTIVE)
        val cancelled = rental.cancel()
        assertEquals(RentalStatus.CANCELLED, cancelled.status)
    }

    private fun sampleRental(
        status: RentalStatus = RentalStatus.RESERVED,
        startDate: Instant = now,
        endDate: Instant = nextWeek,
        totalAmount: Int = 5000
    ) = Rental(
        id = RentalId("rental-001"),
        rentalNumber = "RNT-001",
        customerId = CustomerId("cust-001"),
        vehicleId = VehicleId("veh-001"),
        status = status,
        startDate = startDate,
        endDate = endDate,
        dailyRateAmount = 1000,
        totalAmount = totalAmount
    )
}
