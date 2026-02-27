package com.solodev.fleet.modules.vehicles.application.dto

import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.shared.domain.model.Location
import kotlinx.serialization.Serializable

@Serializable
data class VehicleResponse(
        val id: String,
        val vin: String,
        val licensePlate: String,
        val make: String,
        val model: String,
        val year: Int,
        val color: String?,
        val state: String,
        val mileageKm: Int,
        val dailyRate: Double?,
        val currencyCode: String,
        val passengerCapacity: Int?,
        val lastLocation: Location? = null,
        val routeProgress: Double = 0.0,
        val bearing: Double = 0.0
) {
        companion object {
                fun fromDomain(v: Vehicle) =
                        VehicleResponse(
                                id = v.id.value,
                                vin = v.vin,
                                licensePlate = v.licensePlate,
                                make = v.make,
                                model = v.model,
                                year = v.year,
                                color = v.color,
                                state = v.state.name,
                                mileageKm = v.mileageKm,
                                dailyRate = v.dailyRateAmount?.let { it / 100.0 },
                                currencyCode = v.currencyCode,
                                passengerCapacity = v.passengerCapacity,
                                lastLocation = v.lastLocation,
                                routeProgress = v.routeProgress,
                                bearing = v.bearing
                        )
        }
}
