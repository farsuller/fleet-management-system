package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.maintenance.domain.model.*
import com.solodev.fleet.modules.maintenance.domain.repository.MaintenanceRepository
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import java.time.Instant
import java.util.*

/**
 * Use case for reporting a new vehicle incident by a driver or operator.
 */
class ReportIncidentUseCase(
    private val repository: MaintenanceRepository
) {
    suspend operator fun invoke(
        vehiclePlate: String,
        title: String,
        description: String,
        severity: IncidentSeverity,
        reportedByUserId: UUID?,
        odometerKm: Int? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): Result<VehicleIncident> = try {
        // Here we'd typically look up the vehicle by plate to get its ID
        // But for now, we'll assume the plate acts as or can be converted to VehicleId if needed
        // Assuming VehicleId(plate) for simplicity or if that's the system's plate-as-id policy
        val vehicleId = VehicleId(vehiclePlate)
        
        val incident = VehicleIncident(
            id = IncidentId(UUID.randomUUID()),
            vehicleId = vehicleId,
            title = title,
            description = description,
            severity = severity,
            status = IncidentStatus.REPORTED,
            reportedAt = Instant.now(),
            reportedByUserId = reportedByUserId,
            odometerKm = odometerKm,
            latitude = latitude,
            longitude = longitude
        )
        
        Result.success(repository.saveIncident(incident))
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * Use case for listing incidents.
 */
class ListIncidentsUseCase(
    private val repository: MaintenanceRepository
) {
    suspend fun getActiveByVehicle(vehicleId: String): List<VehicleIncident> {
        return repository.findActiveIncidentsByVehicleId(VehicleId(vehicleId))
    }

    suspend fun getAllByVehicle(vehicleId: String): List<VehicleIncident> {
        return repository.findIncidentsByVehicleId(VehicleId(vehicleId))
    }
    
    suspend fun getByJob(jobId: String): List<VehicleIncident> {
        return repository.findIncidentsByJobId(MaintenanceJobId(jobId))
    }
}
