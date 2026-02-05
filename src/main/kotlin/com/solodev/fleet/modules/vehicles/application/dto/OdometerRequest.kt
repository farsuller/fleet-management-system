package com.solodev.fleet.modules.vehicles.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class OdometerRequest(
    val mileageKm: Int
) {
    init {
        require(mileageKm >= 0) { "Mileage cannot be negative" }
    }
}