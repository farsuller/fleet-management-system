package com.solodev.fleet.modules.maintenance.infrastructure.persistence

import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp

/** Table definition for vehicle incidents. */
object VehicleIncidentsTable : UUIDTable("vehicle_incidents") {
    val vehicleId = reference("vehicle_id", VehiclesTable, onDelete = ReferenceOption.CASCADE)
    val maintenanceJobId = reference("maintenance_job_id", MaintenanceJobsTable, onDelete = ReferenceOption.SET_NULL).nullable()
    
    val title = varchar("title", 200)
    val description = text("description")
    val severity = varchar("severity", 20)
    val status = varchar("status", 20)
    
    val reportedAt = timestamp("reported_at")
    val reportedByUserId = uuid("reported_by_user_id").nullable()
    
    val odometerKm = integer("odometer_km").nullable()
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()

    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")
}
