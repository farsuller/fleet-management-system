package com.solodev.fleet.modules.rentals.infrastructure.persistence

import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

/** Exposed table definition for customers. */
object CustomersTable : UUIDTable("customers") {
    val userId = uuid("user_id").nullable().uniqueIndex()
    val firstName = varchar("first_name", 100)
    val lastName = varchar("last_name", 100)
    val email = varchar("email", 255).uniqueIndex()
    val phone = varchar("phone", 20)
    val driverLicenseNumber = varchar("driver_license_number", 50).uniqueIndex()
    val driverLicenseExpiry = date("driver_license_expiry")
    val address = text("address").nullable()
    val city = varchar("city", 100).nullable()
    val state = varchar("state", 100).nullable()
    val postalCode = varchar("postal_code", 20).nullable()
    val country = varchar("country", 100).nullable()
    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}

/** Exposed table definition for rentals. */
object RentalsTable : UUIDTable("rentals") {
    val rentalNumber = varchar("rental_number", 50).uniqueIndex()
    val customerId = reference("customer_id", CustomersTable, onDelete = ReferenceOption.RESTRICT)
    val vehicleId = reference("vehicle_id", VehiclesTable, onDelete = ReferenceOption.RESTRICT)
    val status = varchar("status", 20)

    // Rental period
    val startDate = timestamp("start_date")
    val endDate = timestamp("end_date")
    val actualStartDate = timestamp("actual_start_date").nullable()
    val actualEndDate = timestamp("actual_end_date").nullable()

    // Pricing
    val dailyRateCents = integer("daily_rate_cents")
    val totalAmountCents = integer("total_amount_cents")
    val currencyCode = varchar("currency_code", 3).default("PHP")

    // Odometer
    val startOdometerKm = integer("start_odometer_km").nullable()
    val endOdometerKm = integer("end_odometer_km").nullable()

    // Locations
    val pickupLocation = varchar("pickup_location", 255).nullable()
    val dropoffLocation = varchar("dropoff_location", 255).nullable()

    // Metadata
    val notes = text("notes").nullable()
    val createdByUserId = uuid("created_by_user_id").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(0)
}

/** Exposed table definition for rental periods (for double-booking prevention). */
object RentalPeriodsTable : UUIDTable("rental_periods") {
    val rentalId =
            reference("rental_id", RentalsTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val vehicleId = reference("vehicle_id", VehiclesTable, onDelete = ReferenceOption.RESTRICT)
    val status = varchar("status", 20)
    // Note: period TSTZRANGE is handled by PostgreSQL trigger, not directly by Exposed
}

/** Exposed table definition for rental charges. */
object RentalChargesTable : UUIDTable("rental_charges") {
    val rentalId = reference("rental_id", RentalsTable, onDelete = ReferenceOption.CASCADE)
    val chargeType = varchar("charge_type", 50)
    val description = text("description")
    val amountCents = integer("amount_cents")
    val currencyCode = varchar("currency_code", 3).default("PHP")
    val chargedAt = timestamp("charged_at")
    val chargedByUserId = uuid("charged_by_user_id").nullable()
}

/** Exposed table definition for rental payments. */
object RentalPaymentsTable : UUIDTable("rental_payments") {
    val rentalId = reference("rental_id", RentalsTable, onDelete = ReferenceOption.CASCADE)
    val paymentMethod = varchar("payment_method", 50)
    val amountCents = integer("amount_cents")
    val currencyCode = varchar("currency_code", 3).default("PHP")
    val transactionReference = varchar("transaction_reference", 255).nullable()
    val status = varchar("status", 20)
    val paidAt = timestamp("paid_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
