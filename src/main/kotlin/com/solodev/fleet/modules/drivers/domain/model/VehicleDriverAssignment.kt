package com.solodev.fleet.modules.drivers.domain.model

import java.time.Instant

/** Represents one period during which a driver was assigned to a vehicle. */
data class VehicleDriverAssignment(
    val id: String,
    val vehicleId: String,
    val driverId: String,
    val assignedAt: Instant,
    val releasedAt: Instant? = null,   // null = currently active
    val notes: String? = null,
) {
    val isActive: Boolean get() = releasedAt == null
}
