package com.solodev.fleet.modules.maintenance.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceStatusUpdateRequest(
    val laborCostPhp: Long = 0,
    val partsCostPhp: Long = 0
) {
    init {
        require(laborCostPhp >= 0) { "Labor cost cannot be negative" }
        require(partsCostPhp >= 0) { "Parts cost cannot be negative" }
    }
}
