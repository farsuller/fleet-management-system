package com.solodev.fleet.modules.vehicles.application.dto

import com.solodev.fleet.modules.vehicles.domain.model.Truck
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleType
import com.solodev.fleet.shared.domain.model.Location
import kotlinx.serialization.Serializable

@Serializable
data class TruckRequest(
    val vin: String? = null,
    val licensePlate: String,
    val make: String,
    val model: String,
    val year: Int,
    val color: String? = null,
    val mileageKm: Int = 0,
    val dailyRate: Double? = null,
    val passengerCapacity: Int? = null,
    val lastServiceMileage: Int? = null,
    val nextServiceMileage: Int? = null,
    val payloadCapacityTons: Double? = null,
    val cargoType: String? = null,
    val axleCount: Int = 2,
    val grossVehicleWeightKg: Int? = null,
    val hasTrailerHitch: Boolean = false,
)

@Serializable
data class TruckUpdateRequest(
    val licensePlate: String? = null,
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
    val color: String? = null,
    val mileageKm: Int? = null,
    val dailyRate: Double? = null,
    val passengerCapacity: Int? = null,
    val lastServiceMileage: Int? = null,
    val nextServiceMileage: Int? = null,
    val payloadCapacityTons: Double? = null,
    val cargoType: String? = null,
    val axleCount: Int? = null,
    val grossVehicleWeightKg: Int? = null,
    val hasTrailerHitch: Boolean? = null,
) {
    fun hasUpdates(): Boolean =
        licensePlate != null ||
            make != null ||
            model != null ||
            year != null ||
            color != null ||
            mileageKm != null ||
            dailyRate != null ||
            passengerCapacity != null ||
            lastServiceMileage != null ||
            nextServiceMileage != null ||
            payloadCapacityTons != null ||
            cargoType != null ||
            axleCount != null ||
            grossVehicleWeightKg != null ||
            hasTrailerHitch != null
}

@Serializable
data class TruckResponse(
    val id: String,
    val vehicleId: String,
    val vin: String? = null,
    val licensePlate: String,
    val make: String,
    val model: String,
    val year: Int,
    val color: String? = null,
    val vehicleType: String,
    val state: String,
    val mileageKm: Int,
    val dailyRate: Double?,
    val currencyCode: String,
    val passengerCapacity: Int?,
    val lastLocation: Location? = null,
    val routeProgress: Double,
    val bearing: Double,
    val lastServiceMileage: Int?,
    val nextServiceMileage: Int?,
    val payloadCapacityTons: Double? = null,
    val cargoType: String? = null,
    val axleCount: Int,
    val grossVehicleWeightKg: Int? = null,
    val hasTrailerHitch: Boolean,
) {
    companion object {
        fun fromDomain(t: Truck) =
            TruckResponse(
                id = t.vehicle.id.value,
                vehicleId = t.vehicle.id.value,
                vin = t.vehicle.vin,
                licensePlate = t.vehicle.licensePlate,
                make = t.vehicle.make,
                model = t.vehicle.model,
                year = t.vehicle.year,
                color = t.vehicle.color,
                vehicleType = t.vehicle.vehicleType.name,
                state = t.vehicle.state.name,
                mileageKm = t.vehicle.mileageKm,
                dailyRate = t.vehicle.dailyRateAmount?.let { it / 100.0 },
                currencyCode = t.vehicle.currencyCode,
                passengerCapacity = t.vehicle.passengerCapacity,
                lastLocation = t.vehicle.lastLocation,
                routeProgress = t.vehicle.routeProgress,
                bearing = t.vehicle.bearing,
                lastServiceMileage = t.vehicle.lastServiceMileage,
                nextServiceMileage = t.vehicle.nextServiceMileage,
                payloadCapacityTons = t.payloadCapacityTons,
                cargoType = t.cargoType,
                axleCount = t.axleCount,
                grossVehicleWeightKg = t.grossVehicleWeightKg,
                hasTrailerHitch = t.hasTrailerHitch,
            )

        fun toVehicle(
            request: TruckRequest,
            id: String,
        ): Vehicle =
            Vehicle(
                id =
                    com.solodev.fleet.modules.vehicles.domain.model
                        .VehicleId(id),
                vin = request.vin,
                licensePlate = request.licensePlate,
                make = request.make,
                model = request.model,
                year = request.year,
                color = request.color,
                vehicleType = VehicleType.TRUCK,
                mileageKm = request.mileageKm,
                dailyRateAmount = request.dailyRate?.let { (it * 100).toInt() },
                passengerCapacity = request.passengerCapacity,
                lastServiceMileage = request.lastServiceMileage,
                nextServiceMileage = request.nextServiceMileage,
            )
    }
}
