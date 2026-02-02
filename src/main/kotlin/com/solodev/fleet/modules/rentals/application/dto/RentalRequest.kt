package com.solodev.fleet.modules.rentals.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class RentalRequest(
        val vehicleId: String,
        val customerId: String,
        val startDate: String, // ISO-8601 string
        val endDate: String, // ISO-8601 string
        val dailyRateCents: Int
)
