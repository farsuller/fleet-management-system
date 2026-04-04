package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

// AVAILABLE, RENTED, MAINTENANCE, RETIRED

@Serializable
data class VehicleStateRequest(
    val state: String,
) {
    init {
        require(state.isNotBlank()) { "State cannot be blank" }
        require(state in listOf("AVAILABLE", "RENTED", "MAINTENANCE", "RETIRED")) {
            "State must be one of: AVAILABLE, RENTED, MAINTENANCE, RETIRED"
        }
    }
}
