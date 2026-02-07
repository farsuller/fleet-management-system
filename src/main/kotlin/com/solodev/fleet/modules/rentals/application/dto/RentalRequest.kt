package com.solodev.fleet.modules.rentals.application.dto

import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class RentalRequest(
        val vehicleId: String,
        val customerId: String,
        val startDate: String, // ISO-8601
        val endDate: String // ISO-8601
) {
        init {
                require(vehicleId.isNotBlank()) { "Vehicle ID cannot be blank" }
                require(customerId.isNotBlank()) { "Customer ID cannot be blank" }
                require(startDate.isNotBlank()) { "Start date cannot be blank" }
                require(endDate.isNotBlank()) { "End date cannot be blank" }

                // Validate date parsing
                val start = Instant.parse(startDate)
                val end = Instant.parse(endDate)
                require(end.isAfter(start)) { "End date must be after start date" }
        }
}
