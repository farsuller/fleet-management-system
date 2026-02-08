package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleUpdateRequest(
        val licensePlate: String? = null,
        val color: String? = null,
        val dailyRate: Double? = null
) {
    init {
        dailyRate?.let { require(it >= 0.0) { "Daily rate cannot be negative" } }
    }

    fun hasUpdates(): Boolean = licensePlate != null || color != null || dailyRate != null
}
