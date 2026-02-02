package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.domain.models.Rental
import kotlinx.serialization.Serializable

@Serializable
data class RentalResponse(
        val id: String,
        val rentalNumber: String,
        val vehicleId: String,
        val customerId: String,
        val status: String,
        val startDate: String,
        val endDate: String
) {
    companion object {
        fun fromDomain(r: Rental) =
                RentalResponse(
                        id = r.id.value,
                        rentalNumber = r.rentalNumber,
                        vehicleId = r.vehicleId.value,
                        customerId = r.customerId.value,
                        status = r.status.name,
                        startDate = r.startDate.toString(),
                        endDate = r.endDate.toString()
                )
    }
}
