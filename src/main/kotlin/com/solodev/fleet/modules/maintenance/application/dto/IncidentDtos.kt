package com.solodev.fleet.modules.maintenance.application.dto

import com.solodev.fleet.modules.maintenance.domain.model.IncidentSeverity
import com.solodev.fleet.modules.maintenance.domain.model.IncidentStatus
import com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class ReportIncidentRequest(
    val title: String,
    val description: String,
    val severity: String, // String to be mapped to enum
    val odometerKm: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class IncidentResponse(
    val id: String,
    val vehicleId: String,
    val vehiclePlate: String? = null,
    val title: String,
    val description: String,
    val severity: String,
    val status: String,
    val reportedAt: String,
    val reportedByUserId: String? = null,
    val maintenanceJobId: String? = null,
    val odometerKm: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    companion object {
        fun fromDomain(incident: VehicleIncident, vehiclePlate: String? = null) = IncidentResponse(
            id = incident.id.value.toString(),
            vehicleId = incident.vehicleId.value,
            vehiclePlate = vehiclePlate,
            title = incident.title,
            description = incident.description,
            severity = incident.severity.name,
            status = incident.status.name,
            reportedAt = incident.reportedAt.toString(),
            reportedByUserId = incident.reportedByUserId?.toString(),
            maintenanceJobId = incident.maintenanceJobId?.value,
            odometerKm = incident.odometerKm,
            latitude = incident.latitude,
            longitude = incident.longitude
        )
    }
}
