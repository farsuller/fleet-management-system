package com.solodev.fleet.modules.maintenance.domain.repository

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import java.time.Instant
import java.util.UUID

/** Repository interface for MaintenanceJob persistence. */
interface MaintenanceRepository {

    /** Find a maintenance job by its unique identifier. */
    suspend fun findById(id: MaintenanceJobId): MaintenanceJob?

    /** Find a maintenance job by its job number. */
    suspend fun findByJobNumber(jobNumber: String): MaintenanceJob?

    /** Save a new maintenance job or update an existing one. */
    suspend fun saveJob(job: MaintenanceJob): MaintenanceJob

    /** Find all maintenance jobs. */
    suspend fun findAll(): List<MaintenanceJob>

    /** Find all maintenance jobs for a specific vehicle. */
    suspend fun findByVehicleId(vehicleId: VehicleId): List<MaintenanceJob>

    /** Find all maintenance jobs with a specific status. */
    suspend fun findByStatus(status: MaintenanceStatus): List<MaintenanceJob>

    /** Find maintenance jobs scheduled between dates. */
    suspend fun findScheduledBetween(startDate: Instant, endDate: Instant): List<MaintenanceJob>

    /** Find maintenance jobs assigned to a specific user. */
    suspend fun findByAssignedUser(userId: UUID): List<MaintenanceJob>

    // ── Incident methods ──────────────────────────────────────────────────────

    /** Find a vehicle incident by its unique identifier. */
    suspend fun findIncidentById(id: com.solodev.fleet.modules.maintenance.domain.model.IncidentId): com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident?

    /** Find all active (unresolved) incidents for a vehicle. */
    suspend fun findActiveIncidentsByVehicleId(vehicleId: VehicleId): List<com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident>

    /** Find all incidents for a specific vehicle. */
    suspend fun findIncidentsByVehicleId(vehicleId: VehicleId): List<com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident>

    /** Find incidents linked to a specific maintenance job. */
    suspend fun findIncidentsByJobId(jobId: MaintenanceJobId): List<com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident>

    /** Save a new vehicle incident. */
    suspend fun saveIncident(incident: com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident): com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident

    /** Find all incidents globally, optionally filtered by status. */
    suspend fun findAllIncidents(status: com.solodev.fleet.modules.maintenance.domain.model.IncidentStatus? = null): List<com.solodev.fleet.modules.maintenance.domain.model.VehicleIncident>
}