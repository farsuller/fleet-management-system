package com.solodev.fleet.modules.tracking.application.dto

import kotlinx.serialization.Serializable

/** Data Transfer Object for vehicle location updates. */
@Serializable
data class LocationUpdateDTO(
        val latitude: Double,
        val longitude: Double,
        val routeId: String? = null // Optional: Snap to a specific route if provided
)
