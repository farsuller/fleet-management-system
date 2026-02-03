package com.solodev.fleet.modules.domain.models

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
        val notes: String? = null
) {
    init {
        require(jobNumber.isNotBlank()) { "Job number cannot be blank" }
        require(description.isNotBlank()) { "Description cannot be blank" }
        require(laborCostCents >= 0) { "Labor cost cannot be negative" }
        require(partsCostCents >= 0) { "Parts cost cannot be negative" }
        odometerKm?.let { require(it >= 0) { "Odometer reading cannot be negative" } }

        // Validate date logic
        if (startedAt != null) {
            require(startedAt.isAfter(scheduledDate) || startedAt == scheduledDate) {
                "Start date must be on or after scheduled date"
            }
        }
        if (completedAt != null && startedAt != null) {
            require(completedAt.isAfter(startedAt) || completedAt == startedAt) {
                "Completion date must be on or after start date"
            }
        }
    }

    val totalCostCents: Int
        get() = laborCostCents + partsCostCents

    /** Start a scheduled maintenance job. */
    fun start(startTime: Instant, assignedTo: UUID): MaintenanceJob {
        require(status == MaintenanceStatus.SCHEDULED) { "Can only start scheduled jobs" }
        return copy(
                status = MaintenanceStatus.IN_PROGRESS,
                startedAt = startTime,
                assignedToUserId = assignedTo
        )
    }

    /** Complete an in-progress maintenance job. */
    fun complete(
            completionTime: Instant,
            completedBy: UUID,
            laborCost: Int,
            partsCost: Int,
            odometer: Int? = null
    ): MaintenanceJob {
        require(status == MaintenanceStatus.IN_PROGRESS) { "Can only complete in-progress jobs" }
        return copy(
                status = MaintenanceStatus.COMPLETED,
                completedAt = completionTime,
                completedByUserId = completedBy,
                laborCostCents = laborCost,
                partsCostCents = partsCost,
                odometerKm = odometer
        )
    }

    /** Cancel a maintenance job. */
    fun cancel(): MaintenanceJob {
        require(status in listOf(MaintenanceStatus.SCHEDULED, MaintenanceStatus.IN_PROGRESS)) {
            "Can only cancel scheduled or in-progress jobs"
        }
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
