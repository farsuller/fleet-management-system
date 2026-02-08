package com.solodev.fleet.modules.vehicles.infrastructure.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * Exposed table definition for vehicles.
 *
 * This maps to the `vehicles` table created in V002__create_vehicles_schema.sql
 */
object VehiclesTable : UUIDTable("vehicles") {
    val plateNumber = varchar("plate_number", 20).uniqueIndex()
    val make = varchar("make", 100)
    val model = varchar("model", 100)
    val year = integer("year")
    val status = varchar("status", 20)
    val passengerCapacity = integer("passenger_capacity").nullable()
    val currentOdometerKm = integer("current_odometer_km").default(0)
    val vin = varchar("vin", 17).nullable().uniqueIndex()
    val color = varchar("color", 50).nullable()
    val fuelType = varchar("fuel_type", 20).nullable()
    val transmission = varchar("transmission", 20).nullable()
    val dailyRateCents = integer("daily_rate_cents").nullable()
    val currencyCode = varchar("currency_code", 3).default("PHP")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(0)
}

/** Exposed table definition for odometer readings. */
object OdometerReadingsTable : UUIDTable("odometer_readings") {
    val vehicleId = reference("vehicle_id", VehiclesTable)
    val readingKm = integer("reading_km")
    val recordedByUserId = uuid("recorded_by_user_id").nullable()
    val recordedAt = timestamp("recorded_at")
    val notes = text("notes").nullable()
}
