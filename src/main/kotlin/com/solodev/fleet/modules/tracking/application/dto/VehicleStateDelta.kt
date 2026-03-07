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
    val progress: Double? = null,
    val bearing: Double? = null,
    val status: VehicleStatus? = null,
    val distanceFromRoute: Double? = null,
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant
) {
    fun hasChanges(): Boolean {
        return progress != null || bearing != null || status != null || distanceFromRoute != null
    }
}