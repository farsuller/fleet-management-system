package com.solodev.fleet.modules.tracking.application.dto

import com.solodev.fleet.shared.domain.model.Location
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class SensorPing(
    val vehicleId: String,               // Vehicle UUID
    @Contextual
    val location: Location? = null,      // legacy field — keep for backward compat
    // Flat coordinate fields (preferred from mobile)
    val latitude:     Double? = null,
    val longitude:    Double? = null,
    val accuracy:     Double? = null,
    val speed:        Double? = null,
    val heading:      Double? = null,
    @Contextual
    val timestamp: Instant,              // When ping was recorded on device
    val routeId: String? = null,         // Optional: Pre-assigned route for this leg
    // NEW — sensor fusion fields
    val accelX:       Double? = null,
    val accelY:       Double? = null,
    val accelZ:       Double? = null,
    val gyroX:        Double? = null,
    val gyroY:        Double? = null,
    val gyroZ:        Double? = null,
    val batteryLevel: Int?    = null,
) {
    fun resolvedLatitude()  = latitude  ?: location?.latitude
    fun resolvedLongitude() = longitude ?: location?.longitude

    /**
     * Validates ping data for reasonableness.
     */
    fun isValid(): Boolean {
        return resolvedLatitude() != null &&
               resolvedLongitude() != null &&
               (speed == null || speed in 0.0..100.0) &&
               (heading == null || heading in 0.0..360.0) &&
               (accuracy == null || accuracy >= 0.0)
    }

    /** Detect driving events from sensor values. */
    fun hasHarshBrake() = accelX != null && accelX < -4.0
    fun hasHarshAccel() = accelX != null && accelX > 4.0
    fun hasSharpTurn()  = gyroZ  != null && Math.abs(gyroZ) > 1.5
}