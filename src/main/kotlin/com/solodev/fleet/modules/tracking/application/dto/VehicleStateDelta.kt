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
    val timestamp: Instant
) {
    fun hasChanges(): Boolean {
        return routeProgress != null || headingDeg != null || latitude != null ||
               longitude != null || status != null || distanceFromRoute != null
    }
}