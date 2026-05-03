package com.solodev.fleet.modules.vehicles.infrastructure.persistence

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object BusesTable : Table("buses") {
    val vehicleId = reference("vehicle_id", VehiclesTable)
    val routeNumber = varchar("route_number", 20).nullable()
    val doorCount = integer("door_count").default(2)
    val standingCapacity = integer("standing_capacity").nullable()
    val hasAccessibilityRamp = bool("has_accessibility_ramp").default(false)
    val hasAirConditioning = bool("has_air_conditioning").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(vehicleId)
}

object TrucksTable : Table("trucks") {
    val vehicleId = reference("vehicle_id", VehiclesTable)
    val payloadCapacityTons = decimal("payload_capacity_tons", 8, 2).nullable()
    val cargoType = varchar("cargo_type", 30).nullable()
    val axleCount = integer("axle_count").default(2)
    val grossVehicleWeightKg = integer("gross_vehicle_weight_kg").nullable()
    val hasTrailerHitch = bool("has_trailer_hitch").default(false)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(vehicleId)
}
