package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleUpdateRequest(
    val licensePlate: String? = null,
    val color: String? = null,
    val dailyRateCents: Int? = null
) {
    init {
        dailyRateCents?.let {
            require(it >= 0) { "Daily rate cannot be negative" }
        }
    }

    fun hasUpdates(): Boolean =
        licensePlate != null || color != null || dailyRateCents != null
}