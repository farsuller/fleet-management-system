package com.solodev.fleet.modules.maintenance.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceStatusUpdateRequest(
        val status: String,
        val laborCost: Double = 0.0,
        val partsCost: Double = 0.0
) {
    init {
        require(status.isNotBlank()) { "Status required" }
        require(laborCost >= 0.0) { "Labor cost cannot be negative" }
        require(partsCost >= 0.0) { "Parts cost cannot be negative" }
    }
}
