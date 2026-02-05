package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleStateRequest(
    val state: String // AVAILABLE, RENTED, MAINTENANCE, RETIRED
) {
    init {
        require(state.isNotBlank()) { "State cannot be blank" }
        require(state in listOf("AVAILABLE", "RENTED", "MAINTENANCE", "RETIRED")) {
            "State must be one of: AVAILABLE, RENTED, MAINTENANCE, RETIRED"
        }
    }
}