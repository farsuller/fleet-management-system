package com.solodev.fleet.modules.domain.models

import java.time.Instant
import java.util.*

/** Value object representing a unique rental identifier. */
@JvmInline
value class RentalId(val value: String) {
    init {
        require(value.isNotBlank()) { "Rental ID cannot be blank" }
    }
}

/** Value object representing a unique customer identifier. */
@JvmInline
value class CustomerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Customer ID cannot be blank" }
    }
}

/** Rental status in the lifecycle. */
enum class RentalStatus {
    RESERVED,
    ACTIVE,
    COMPLETED,
    CANCELLED
}

/** Payment method enumeration. */
enum class PaymentMethod {
    CREDIT_CARD,
    DEBIT_CARD,
    CASH,
    BANK_TRANSFER
}

/** Payment status enumeration. */
enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}

/**
 * Rental domain entity.
 *
 * Represents a vehicle rental in the fleet management system.
 */
data class Rental(
        val id: RentalId,
        val rentalNumber: String,
        val customerId: CustomerId,
        val vehicleId: VehicleId,
        val status: RentalStatus,
        val startDate: Instant,
        val endDate: Instant,
        val actualStartDate: Instant? = null,
        val actualEndDate: Instant? = null,
        val dailyRateCents: Int,
        val totalAmountCents: Int,
        val currencyCode: String = "PHP",
        val startOdometerKm: Int? = null,
        val endOdometerKm: Int? = null
) {
    init {
        require(endDate.isAfter(startDate)) { "End date must be after start date" }
        require(totalAmountCents >= 0) { "Total amount cannot be negative" }
    }

    fun activate(actualStart: Instant, startOdo: Int): Rental {
        require(status == RentalStatus.RESERVED) { "Rental must be RESERVED" }
        return copy(
                status = RentalStatus.ACTIVE,
                actualStartDate = actualStart,
                startOdometerKm = startOdo
        )
    }

    fun complete(actualEnd: Instant, endOdo: Int): Rental {
        require(status == RentalStatus.ACTIVE) { "Rental must be ACTIVE" }
        return copy(
                status = RentalStatus.COMPLETED,
                actualEndDate = actualEnd,
                endOdometerKm = endOdo
        )
    }

    fun cancel(): Rental = copy(status = RentalStatus.CANCELLED)
}
