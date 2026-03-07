package com.solodev.fleet.modules.tracking.application.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class VehicleRouteState(
    val vehicleId: String,               // Vehicle UUID as string
    val routeId: String,                 // Assigned route UUID as string
    val progress: Double,                // 0.0-1.0 (0% to 100% along route)
    val segmentId: String,               // Current road segment identifier
    val speed: Double,                   // Speed in m/s (from GPS)
    val heading: Double,                 // Bearing 0-360 degrees (north = 0)
    val status: VehicleStatus,           // IN_TRANSIT, IDLE, OFF_ROUTE
    val distanceFromRoute: Double,       // Meters from nearest route point
    val latitude: Double,                // GPS latitude coordinate
    val longitude: Double,               // GPS longitude coordinate
    @Contextual
    val timestamp: Instant               // UTC moment of this state
) {
    /**
     * Returns true if vehicle has deviated significantly from the route.
     */
    fun isOffRoute(toleranceMeters: Double = 100.0): Boolean =
        status == VehicleStatus.OFF_ROUTE || distanceFromRoute > toleranceMeters
}

enum class VehicleStatus {
    IN_TRANSIT,
    IDLE,
    OFF_ROUTE
}