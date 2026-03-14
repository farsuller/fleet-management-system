package com.solodev.fleet.modules.tracking.application.dto

import com.solodev.fleet.shared.infrastructure.serialization.InstantSerializer
import com.solodev.fleet.shared.infrastructure.serialization.UUIDSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

import java.time.Instant
import java.util.UUID

@Serializable
data class VehicleStateDelta(
    @Serializable(with = UUIDSerializer::class)
    val vehicleId: UUID,
    val routeProgress: Double? = null,
    val headingDeg: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: VehicleStatus? = null,
    val distanceFromRoute: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant,
    // NEW — sensor fusion fields
    val accelX:       Double?  = null,
    val accelY:       Double?  = null,
    val accelZ:       Double?  = null,
    val gyroX:        Double?  = null,
    val gyroY:        Double?  = null,
    val gyroZ:        Double?  = null,
    val batteryLevel: Int?     = null,
    val harshBrake:   Boolean? = null,
    val harshAccel:   Boolean? = null,
    val sharpTurn:    Boolean? = null,
) {
    companion object {}

    fun hasChanges(): Boolean {
        return routeProgress != null || headingDeg != null || latitude != null ||
               longitude != null || status != null || distanceFromRoute != null ||
               accelX != null || accelY != null || accelZ != null ||
               gyroX != null || gyroY != null || gyroZ != null ||
               batteryLevel != null || harshBrake != null || harshAccel != null || sharpTurn != null
    }
}