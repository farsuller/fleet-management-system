package com.solodev.fleet.modules.maintenance.application.dto

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJob
import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceResponse(
        val id: String,
        val jobNumber: String,
        val vehicleId: String,
        val status: String,
        val jobType: String,
        val description: String,
        val scheduledDate: String,
        val totalCost: Double
) {
    companion object {
        fun fromDomain(j: MaintenanceJob) =
                MaintenanceResponse(
                        id = j.id.value,
                        jobNumber = j.jobNumber,
                        vehicleId = j.vehicleId.value,
                        status = j.status.name,
                        jobType = j.jobType.name,
                        description = j.description,
                        scheduledDate = j.scheduledDate.toString(),
                        totalCost = j.totalCostCents / 100.0
                )
    }
}
