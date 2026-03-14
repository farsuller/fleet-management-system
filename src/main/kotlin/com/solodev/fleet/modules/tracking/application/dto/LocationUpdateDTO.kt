package com.solodev.fleet.modules.tracking.application.dto

import kotlinx.serialization.Serializable

/** Data Transfer Object for vehicle location updates. */
@Serializable
data class LocationUpdateDTO(
        val latitude: Double,
        val longitude: Double,
        val routeId: String? = null,
        val speed: Double? = null,
        val heading: Double? = null,
        val accuracy: Double? = null,
        val recordedAt: String? = null,
        val accelX: Double? = null,
        val accelY: Double? = null,
        val accelZ: Double? = null,
        val gyroX: Double? = null,
        val gyroY: Double? = null,
        val gyroZ: Double? = null,
        val batteryLevel: Int? = null
)
