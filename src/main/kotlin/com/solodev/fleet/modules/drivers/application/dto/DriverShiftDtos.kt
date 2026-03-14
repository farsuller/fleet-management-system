package com.solodev.fleet.modules.drivers.application.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant
import com.solodev.fleet.modules.drivers.domain.model.DriverShift

@Serializable
data class StartShiftRequest(val vehicleId: String, val notes: String? = null)

@Serializable
data class EndShiftRequest(val notes: String? = null)

@Serializable
data class ShiftResponse(
    val id:        String,
    val driverId:  String,
    val vehicleId: String,
    @Contextual val startedAt: Instant,
    @Contextual val endedAt:   Instant?,
    val notes:     String?,
    val isActive:  Boolean,
) {
    companion object {
        fun fromDomain(s: DriverShift) = ShiftResponse(
            id        = s.id.toString(),
            driverId  = s.driverId.toString(),
            vehicleId = s.vehicleId.toString(),
            startedAt = s.startedAt,
            endedAt   = s.endedAt,
            notes     = s.notes,
            isActive  = s.endedAt == null,
        )
    }
}
