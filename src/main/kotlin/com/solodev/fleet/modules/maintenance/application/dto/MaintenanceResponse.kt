package com.solodev.fleet.modules.maintenance.application.dto

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePriority
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import kotlinx.serialization.Serializable

@Serializable
data class VehicleUsageHistoryDto(
    val rentalNumber: String,
    val customerName: String,
    val startDate: Long,
    val endDate: Long,
    val startOdometer: Int?,
    val endOdometer: Int?,
    val status: String
)

@Serializable
data class MaintenanceResponse(
    val id: String,
    val jobNumber: String,
    val vehicleId: String,
    val vehiclePlate: String? = null,
    val vehicleMake: String? = null,
    val vehicleModel: String? = null,
    val status: MaintenanceStatus,
    val type: MaintenanceJobType,
    val priority: MaintenancePriority,
    val description: String,
    val scheduledDate: Long, // Epoch ms
    val estimatedCostPhp: Long,
    val laborCostPhp: Long,
    val partsCostPhp: Long,
    val totalCostPhp: Long,
    val usageHistory: List<VehicleUsageHistoryDto> = emptyList()
) {
    companion object {
        fun fromDomain(j: MaintenanceJob, history: List<VehicleUsageHistoryDto> = emptyList()) =
            MaintenanceResponse(
                id = j.id.value,
                jobNumber = j.jobNumber,
                vehicleId = j.vehicleId.value,
                vehiclePlate = j.vehiclePlate,
                vehicleMake = j.vehicleMake,
                vehicleModel = j.vehicleModel,
                status = j.status,
                type = j.jobType,
                priority = j.priority,
                description = j.description,
                scheduledDate = j.scheduledDate.toEpochMilli(),
                estimatedCostPhp = j.laborCost.toLong(),
                laborCostPhp = j.laborCost.toLong(),
                partsCostPhp = j.partsCost.toLong(),
                totalCostPhp = j.totalCost.toLong(),
                usageHistory = history
            )
    }
}
