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
    val currencyCode: String = "USD",
    val startOdometerKm: Int? = null,
    val endOdometerKm: Int? = null,
    val pickupLocation: String? = null,
    val dropoffLocation: String? = null,
    val notes: String? = null
) {
    init {
        require(rentalNumber.isNotBlank()) { "Rental number cannot be blank" }
        require(endDate.isAfter(startDate)) { "End date must be after start date" }
        require(dailyRateCents >= 0) { "Daily rate cannot be negative" }
        require(totalAmountCents >= 0) { "Total amount cannot be negative" }
        actualEndDate?.let { end ->
            actualStartDate?.let { start ->
                require(end.isAfter(start) || end == start) { "Actual end date must be after or equal to actual start date" }
            }
        }
        if (startOdometerKm != null && endOdometerKm != null) {
            require(endOdometerKm >= startOdometerKm) { "End odometer must be >= start odometer" }
        }
    }

    /** Activate a reserved rental. */
    fun activate(actualStart: Instant, startOdometer: Int): Rental {
        require(status == RentalStatus.RESERVED) { "Can only activate reserved rentals" }
        return copy(
            status = RentalStatus.ACTIVE,
            actualStartDate = actualStart,
            startOdometerKm = startOdometer
        )
    }

    /** Complete an active rental. */
    fun complete(actualEnd: Instant, endOdometer: Int): Rental {
        require(status == RentalStatus.ACTIVE) { "Can only complete active rentals" }
        return copy(
            status = RentalStatus.COMPLETED,
            actualEndDate = actualEnd,
            endOdometerKm = endOdometer
        )
    }

    /** Cancel a rental. */
    fun cancel(): Rental {
        require(status in listOf(RentalStatus.RESERVED, RentalStatus.ACTIVE)) {
            "Can only cancel reserved or active rentals"
        }
        return copy(status = RentalStatus.CANCELLED)
    }
}

/**
 * Customer domain entity.
 */
data class Customer(
    val id: CustomerId,
    val userId: UUID? = null,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val driverLicenseNumber: String,
    val driverLicenseExpiry: Instant,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null
) {
    init {
        require(firstName.isNotBlank()) { "First name cannot be blank" }
        require(lastName.isNotBlank()) { "Last name cannot be blank" }
        require(email.isNotBlank()) { "Email cannot be blank" }
        require(phone.isNotBlank()) { "Phone cannot be blank" }
        require(driverLicenseNumber.isNotBlank()) { "Driver license number cannot be blank" }
    }

    val fullName: String
        get() = "$firstName $lastName"
}
