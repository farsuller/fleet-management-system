package com.solodev.fleet.modules.maintenance.domain.model

import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import java.time.Instant
import java.util.*

/** Unique identifier for a vehicle incident. */
@JvmInline
value class IncidentId(val value: UUID)

/** Severity of the reported incident. */
enum class IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/** Status of the incident resolution. */
enum class IncidentStatus {
    REPORTED,
    IN_MAINTENANCE,
    RESOLVED,
    DISMISSED
}

/**
 * Domain entity representing a vehicle incident reported by a driver.
 */
data class VehicleIncident(
    val id: IncidentId,
    val vehicleId: VehicleId,
    val title: String,
    val description: String,
    val severity: IncidentSeverity,
    val status: IncidentStatus,
    val reportedAt: Instant,
    val reportedByUserId: UUID?,
    val vehiclePlate: String? = null,
    val maintenanceJobId: MaintenanceJobId? = null,
    val odometerKm: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
