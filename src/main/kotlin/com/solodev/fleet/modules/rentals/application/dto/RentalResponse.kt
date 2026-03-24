package com.solodev.fleet.modules.rentals.application.dto

import com.solodev.fleet.modules.rentals.domain.model.Rental
import kotlinx.serialization.Serializable

@Serializable
data class RentalResponse(
        val id: String,
        val rentalNumber: String,
        val vehicleId: String,
        val customerId: String,
        val status: String,
        val startDate: String,
        val endDate: String,
        val actualStartDate: String?,
        val actualEndDate: String?,
        val startOdometerKm: Int?,
        val endOdometerKm: Int?,
        val dailyRate: Int,
        val totalCost: Int,
        val currencyCode: String,
        val vehiclePlateNumber: String? = null,
        val vehicleMake: String? = null,
        val vehicleModel: String? = null,
        val customerName: String? = null
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
                                endDate = r.endDate.toString(),
                                actualStartDate = r.actualStartDate?.toString(),
                                actualEndDate = r.actualEndDate?.toString(),
                                startOdometerKm = r.startOdometerKm,
                                endOdometerKm = r.endOdometerKm,
                                dailyRate = r.dailyRateAmount,
                                totalCost = r.totalAmount,
                                currencyCode = r.currencyCode
                        )

                fun fromDomain(d: com.solodev.fleet.modules.rentals.domain.repository.RentalWithDetails) =
                        fromDomain(d.rental).copy(
                                vehiclePlateNumber = d.vehiclePlateNumber,
                                vehicleMake = d.vehicleMake,
                                vehicleModel = d.vehicleModel,
                                customerName = d.customerName
                        )
        }
}
