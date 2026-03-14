package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class VehicleUpdateRequest(
        val licensePlate: String? = null,
        val make: String? = null,
        val model: String? = null,
        val year: Int? = null,
        val color: String? = null,
        val dailyRate: Double? = null,
        val mileageKm: Int? = null
) {
    init {
        dailyRate?.let { require(it >= 0.0) { "Daily rate cannot be negative" } }
        year?.let { require(it in 1900..2100) { "Year must be between 1900 and 2100" } }
        mileageKm?.let { require(it >= 0) { "Mileage cannot be negative" } }
    }

    fun hasUpdates(): Boolean =
            licensePlate != null ||
                    make != null ||
                    model != null ||
                    year != null ||
                    color != null ||
                    dailyRate != null ||
                    mileageKm != null
}
