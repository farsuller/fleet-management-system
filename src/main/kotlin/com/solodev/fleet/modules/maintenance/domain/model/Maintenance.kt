package com.solodev.fleet.modules.maintenance.domain.model

import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import java.time.Instant
import java.util.*

/** Value object representing a unique maintenance job identifier. */
@JvmInline
value class MaintenanceJobId(val value: String) {
    init {
        require(value.isNotBlank()) { "Maintenance job ID cannot be blank" }
    }
}

/** Maintenance job status. */
enum class MaintenanceStatus {
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

/** Maintenance job type. */
enum class MaintenanceJobType {
    ROUTINE,
    REPAIR,
    INSPECTION,
    RECALL,
    EMERGENCY
}

/** Maintenance priority. */
enum class MaintenancePriority {
    LOW,
    NORMAL,
    HIGH,
    URGENT
}

/**
 * Maintenance job domain entity.
 *
 * Represents a maintenance job for a vehicle.
 */
data class MaintenanceJob(
    val id: MaintenanceJobId,
    val jobNumber: String,
    val vehicleId: VehicleId,
    val status: MaintenanceStatus,
    val jobType: MaintenanceJobType,
    val description: String,
    val priority: MaintenancePriority = MaintenancePriority.NORMAL,
    val scheduledDate: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val odometerKm: Int? = null,
    val laborCostCents: Int = 0,
    val partsCostCents: Int = 0,
    val currencyCode: String = "PHP",
    val assignedToUserId: UUID? = null,
    val completedByUserId: UUID? = null,
    val notes: String? = null,
) {
    init {
        require(laborCostCents >= 0) { "Labor cost cannot be negative" }
        require(partsCostCents >= 0) { "Parts cost cannot be negative" }
    }

    val totalCostCents: Int get() = laborCostCents + partsCostCents

    fun start(timestamp: Instant = Instant.now()): MaintenanceJob {
        require(status == MaintenanceStatus.SCHEDULED) { "Only SCHEDULED jobs can be started." }
        return copy(status = MaintenanceStatus.IN_PROGRESS, startedAt = timestamp)
    }

    fun complete(labor: Int, parts: Int, timestamp: Instant = Instant.now()): MaintenanceJob {
        require(status == MaintenanceStatus.IN_PROGRESS) { "Only IN_PROGRESS jobs can be completed." }
        return copy(
            status = MaintenanceStatus.COMPLETED,
            completedAt = timestamp,
            laborCostCents = labor,
            partsCostCents = parts
        )
    }

    fun cancel(): MaintenanceJob {
        require(status == MaintenanceStatus.SCHEDULED) { "Cannot cancel job that has already started." }
        return copy(status = MaintenanceStatus.CANCELLED)
    }
}

/** Maintenance part used in a job. */
data class MaintenancePart(
    val id: UUID,
    val jobId: MaintenanceJobId,
    val partNumber: String,
    val partName: String,
    val quantity: Int,
    val unitCostCents: Int,
    val currencyCode: String = "PHP",
    val supplier: String? = null,
    val notes: String? = null
) {
    init {
        require(partNumber.isNotBlank()) { "Part number cannot be blank" }
        require(partName.isNotBlank()) { "Part name cannot be blank" }
        require(quantity > 0) { "Quantity must be positive" }
        require(unitCostCents >= 0) { "Unit cost cannot be negative" }
    }

    val totalCostCents: Int
        get() = quantity * unitCostCents
}
