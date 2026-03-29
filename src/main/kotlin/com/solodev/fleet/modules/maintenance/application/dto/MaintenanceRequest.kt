package com.solodev.fleet.modules.maintenance.application.dto

import kotlinx.serialization.Serializable
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePriority


@Serializable
data class MaintenanceRequest(
    val vehicleId: String,
    val type: MaintenanceJobType,
    val priority: MaintenancePriority,
    val description: String,
    val scheduledDate: Long, // Epoch ms
    val estimatedCostPhp: Long // Amount in sub-units (e.g., cents)
) {
    init {
        require(vehicleId.isNotBlank()) { "Vehicle ID required" }
        require(description.length >= 10) { "Description too short" }
    }
}