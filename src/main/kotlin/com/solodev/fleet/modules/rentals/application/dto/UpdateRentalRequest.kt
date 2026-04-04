package com.solodev.fleet.modules.rentals.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class UpdateRentalRequest(
    val startDate: String? = null,
    val endDate: String? = null,
    val dailyRateAmount: Long? = null,
    val vehicleId: String? = null,
    val customerId: String? = null,
)
