package com.solodev.fleet.modules.infrastructure.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp

/** Exposed table definition for maintenance jobs. */
object MaintenanceJobsTable : UUIDTable("maintenance_jobs") {
    val jobNumber = varchar("job_number", 50).uniqueIndex()
    val vehicleId = reference("vehicle_id", VehiclesTable, onDelete = ReferenceOption.RESTRICT)
    val status = varchar("status", 20)

    // Job details
    val jobType = varchar("job_type", 50)
    val description = text("description")
    val priority = varchar("priority", 20).default("NORMAL")

    // Scheduling
    val scheduledDate = timestamp("scheduled_date")
    val startedAt = timestamp("started_at").nullable()
    val completedAt = timestamp("completed_at").nullable()

    // Odometer
    val odometerKm = integer("odometer_km").nullable()

    // Cost tracking
    val laborCostCents = integer("labor_cost_cents").default(0)
    val partsCostCents = integer("parts_cost_cents").default(0)
    val currencyCode = varchar("currency_code", 3).default("PHP")

    // Personnel
    val assignedToUserId = uuid("assigned_to_user_id").nullable()
    val completedByUserId = uuid("completed_by_user_id").nullable()

    // Metadata
    val notes = text("notes").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
    val version = long("version").default(0)
}

/** Exposed table definition for maintenance parts. */
object MaintenancePartsTable : UUIDTable("maintenance_parts") {
    val jobId = reference("job_id", MaintenanceJobsTable, onDelete = ReferenceOption.CASCADE)
    val partNumber = varchar("part_number", 100)
    val partName = varchar("part_name", 255)
    val quantity = integer("quantity")
    val unitCostCents = integer("unit_cost_cents")
    val currencyCode = varchar("currency_code", 3).default("PHP")
    val supplier = varchar("supplier", 255).nullable()
    val notes = text("notes").nullable()
    val addedAt = timestamp("added_at")
}

/** Exposed table definition for maintenance schedules. */
object MaintenanceSchedulesTable : UUIDTable("maintenance_schedules") {
    val vehicleId = reference("vehicle_id", VehiclesTable, onDelete = ReferenceOption.CASCADE)
    val scheduleType = varchar("schedule_type", 50)
    val description = text("description")

    // Recurrence rules
    val intervalType = varchar("interval_type", 20)
    val mileageIntervalKm = integer("mileage_interval_km").nullable()
    val timeIntervalDays = integer("time_interval_days").nullable()

    // Last service tracking
    val lastServiceDate = timestamp("last_service_date").nullable()
    val lastServiceOdometerKm = integer("last_service_odometer_km").nullable()

    // Next service due
    val nextServiceDate = timestamp("next_service_date").nullable()
    val nextServiceOdometerKm = integer("next_service_odometer_km").nullable()

    val isActive = bool("is_active").default(true)
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
