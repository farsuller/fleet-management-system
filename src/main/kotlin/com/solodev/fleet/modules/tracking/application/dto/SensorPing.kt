package com.solodev.fleet.modules.tracking.application.dto

import com.solodev.fleet.shared.domain.model.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SensorPing(
    val vehicleId: String,               // Vehicle UUID
    @Contextual
    val location: Location,              // GPS coordinates (lat, lng)
    val speed: Double?,                  // Speed in m/s (null if stationary)
    val heading: Double?,                // Bearing 0-360 degrees (null if unavailable)
    val accuracy: Double? = null,        // GPS accuracy in meters
    @Contextual
    val timestamp: Instant,              // When ping was recorded on device
    val routeId: String? = null          // Optional: Pre-assigned route for this leg
) {
    /**
     * Validates ping data for reasonableness.
     */
    fun isValid(): Boolean {
        return (speed == null || speed in 0.0..100.0) &&
                (heading == null || heading in 0.0..360.0) &&
                (accuracy == null || accuracy >= 0.0)
    }
}