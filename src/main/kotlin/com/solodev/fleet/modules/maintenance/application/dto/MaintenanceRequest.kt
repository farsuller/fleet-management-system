package com.solodev.fleet.modules.maintenance.application.dto

import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePriority
import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceRequest(
    val vehicleId: String,
    val type: MaintenanceJobType,
    val priority: MaintenancePriority,
    val description: String,
    val scheduledDate: Long,
    val estimatedCostPhp: Long,
) {
    init {
        require(vehicleId.isNotBlank()) { "Vehicle ID required" }
        require(description.length >= 10) { "Description too short" }
    }
}
