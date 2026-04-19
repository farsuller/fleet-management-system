package com.solodev.fleet.modules.vehicles.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Truck(
    val vehicle: Vehicle,
    val payloadCapacityTons: Double? = null,
    val cargoType: String? = null,
    val axleCount: Int = 2,
    val grossVehicleWeightKg: Int? = null,
    val hasTrailerHitch: Boolean = false,
) {
    init {
        require(vehicle.vehicleType == VehicleType.TRUCK) { "Truck must use vehicleType=TRUCK" }
        require(axleCount > 0) { "Axle count must be positive" }
        payloadCapacityTons?.let { require(it >= 0.0) { "Payload capacity cannot be negative" } }
        grossVehicleWeightKg?.let { require(it >= 0) { "Gross vehicle weight cannot be negative" } }
        cargoType?.let {
            val allowed = setOf("FLATBED", "REFRIGERATED", "TANKER", "BOX", "CURTAINSIDER")
            require(it in allowed) { "Invalid cargo type: $it" }
        }
    }
}
