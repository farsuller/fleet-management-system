package com.solodev.fleet.modules.vehicles.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Bus(
    val vehicle: Vehicle,
    val routeNumber: String? = null,
    val doorCount: Int = 2,
    val standingCapacity: Int? = null,
    val hasAccessibilityRamp: Boolean = false,
    val hasAirConditioning: Boolean = false,
) {
    init {
        require(vehicle.vehicleType == VehicleType.BUS) { "Bus must use vehicleType=BUS" }
        require(doorCount > 0) { "Door count must be positive" }
        standingCapacity?.let { require(it >= 0) { "Standing capacity cannot be negative" } }
    }
}
