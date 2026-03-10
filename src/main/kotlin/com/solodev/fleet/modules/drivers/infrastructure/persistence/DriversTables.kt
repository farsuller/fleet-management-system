package com.solodev.fleet.modules.drivers.infrastructure.persistence

import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.timestamp

object DriversTable : UUIDTable("drivers") {
    val userId          = uuid("user_id").nullable().uniqueIndex()
    val firstName       = varchar("first_name", 100)
    val lastName        = varchar("last_name", 100)
    val email           = varchar("email", 255).uniqueIndex()
    val phone           = varchar("phone", 20)
    val licenseNumber   = varchar("license_number", 50).uniqueIndex()
    val licenseExpiry   = date("license_expiry")
    val licenseClass    = varchar("license_class", 20).nullable()
    val address         = text("address").nullable()
    val city            = varchar("city", 100).nullable()
    val state           = varchar("state", 100).nullable()
    val postalCode      = varchar("postal_code", 20).nullable()
    val country         = varchar("country", 100).nullable()
    val isActive        = bool("is_active").default(true)
    val createdAt       = timestamp("created_at")
    val updatedAt       = timestamp("updated_at")
}

object VehicleDriverAssignmentsTable : UUIDTable("vehicle_driver_assignments") {
    val vehicleId   = reference("vehicle_id", VehiclesTable, onDelete = ReferenceOption.CASCADE)
    val driverId    = reference("driver_id",  DriversTable,  onDelete = ReferenceOption.CASCADE)
    val assignedAt  = timestamp("assigned_at")
    val releasedAt  = timestamp("released_at").nullable()
    val notes       = text("notes").nullable()
}
