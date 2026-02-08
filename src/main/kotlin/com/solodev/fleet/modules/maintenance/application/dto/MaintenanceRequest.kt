package com.solodev.fleet.modules.maintenance.application.dto

import kotlinx.serialization.Serializable
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType


@Serializable
data class MaintenanceRequest(
    val vehicleId: String,
    val jobType: String,
    val description: String,
    val scheduledDate: String // ISO-8601
) {
    init {
        require(vehicleId.isNotBlank()) { "Vehicle ID required" }
        require(description.length >= 10) { "Description too short" }
        require(MaintenanceJobType.entries.any { it.name == jobType }) { "Invalid job type" }
    }
}