package com.solodev.fleet.modules.domain.ports

import com.solodev.fleet.modules.domain.models.*
import java.time.Instant
import java.util.*

/**
 * Repository interface for MaintenanceJob persistence.
 */
interface MaintenanceRepository {
    /**
     * Find a maintenance job by its unique identifier.
     */
    suspend fun findById(id: MaintenanceJobId): MaintenanceJob?

    /**
     * Find a maintenance job by its job number.
     */
    suspend fun findByJobNumber(jobNumber: String): MaintenanceJob?

    /**
     * Save a new maintenance job or update an existing one.
     */
    suspend fun save(job: MaintenanceJob): MaintenanceJob

    /**
     * Find all maintenance jobs for a specific vehicle.
     */
    suspend fun findByVehicleId(vehicleId: VehicleId): List<MaintenanceJob>

    /**
     * Find all maintenance jobs with a specific status.
     */
    suspend fun findByStatus(status: MaintenanceStatus): List<MaintenanceJob>

    /**
     * Find maintenance jobs scheduled between dates.
     */
    suspend fun findScheduledBetween(startDate: Instant, endDate: Instant): List<MaintenanceJob>

    /**
     * Find maintenance jobs assigned to a specific user.
     */
    suspend fun findByAssignedUser(userId: UUID): List<MaintenanceJob>

    /**
     * Delete a maintenance job by ID.
     */
    suspend fun deleteById(id: MaintenanceJobId): Boolean

    /**
     * Add a part to a maintenance job.
     */
    suspend fun addPart(part: MaintenancePart): MaintenancePart

    /**
     * Find all parts used in a maintenance job.
     */
    suspend fun findPartsByJobId(jobId: MaintenanceJobId): List<MaintenancePart>
}
