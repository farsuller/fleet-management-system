package com.solodev.fleet.modules.vehicles.application.dto

import com.solodev.fleet.modules.domain.models.Vehicle
import kotlinx.serialization.Serializable

@Serializable
data class VehicleResponse(
        val id: String,
        val plateNumber: String,
        val make: String,
        val model: String,
        val year: Int,
        val status: String,
        val capacity: Int?
) {
        companion object {
                fun fromDomain(v: Vehicle) =
                        VehicleResponse(
                                id = v.id.value,
                                plateNumber = v.plateNumber,
                                make = v.make,
                                model = v.model,
                                year = v.year,
                                status = v.status.name,
                                capacity = v.passengerCapacity
                        )
        }
}
