package com.solodev.fleet.modules.drivers.domain.model

import java.time.Instant
import java.util.UUID

data class DriverShift(
    val id:        UUID,
    val driverId:  UUID,
    val vehicleId: UUID,
    val startedAt: Instant,
    val endedAt:   Instant? = null,    // null = active shift
    val notes:     String?  = null,
)
