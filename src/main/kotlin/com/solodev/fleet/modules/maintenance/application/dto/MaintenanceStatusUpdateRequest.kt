package com.solodev.fleet.modules.maintenance.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceStatusUpdateRequest(
    val status: String,
    val laborCostCents: Int = 0,
    val partsCostCents: Int = 0
) {
    init {
        require(status.isNotBlank()) { "Status required" }
        require(laborCostCents >= 0) { "Labor cost cannot be negative" }
        require(partsCostCents >= 0) { "Parts cost cannot be negative" }
    }
}