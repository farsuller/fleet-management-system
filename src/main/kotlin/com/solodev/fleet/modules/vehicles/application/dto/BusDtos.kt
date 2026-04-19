package com.solodev.fleet.modules.vehicles.application.dto

import com.solodev.fleet.modules.vehicles.domain.model.Bus
import com.solodev.fleet.modules.vehicles.domain.model.Vehicle
import com.solodev.fleet.modules.vehicles.domain.model.VehicleType
import com.solodev.fleet.shared.domain.model.Location
import kotlinx.serialization.Serializable

@Serializable
data class BusRequest(
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
    val routeNumber: String? = null,
    val doorCount: Int = 2,
    val standingCapacity: Int? = null,
    val hasAccessibilityRamp: Boolean = false,
    val hasAirConditioning: Boolean = false,
)

@Serializable
data class BusUpdateRequest(
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
    val routeNumber: String? = null,
    val doorCount: Int? = null,
    val standingCapacity: Int? = null,
    val hasAccessibilityRamp: Boolean? = null,
    val hasAirConditioning: Boolean? = null,
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
            routeNumber != null ||
            doorCount != null ||
            standingCapacity != null ||
            hasAccessibilityRamp != null ||
            hasAirConditioning != null
}

@Serializable
data class BusResponse(
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
    val routeNumber: String? = null,
    val doorCount: Int,
    val standingCapacity: Int? = null,
    val hasAccessibilityRamp: Boolean,
    val hasAirConditioning: Boolean,
) {
    companion object {
        fun fromDomain(b: Bus) =
            BusResponse(
                id = b.vehicle.id.value,
                vehicleId = b.vehicle.id.value,
                vin = b.vehicle.vin,
                licensePlate = b.vehicle.licensePlate,
                make = b.vehicle.make,
                model = b.vehicle.model,
                year = b.vehicle.year,
                color = b.vehicle.color,
                vehicleType = b.vehicle.vehicleType.name,
                state = b.vehicle.state.name,
                mileageKm = b.vehicle.mileageKm,
                dailyRate = b.vehicle.dailyRateAmount?.let { it / 100.0 },
                currencyCode = b.vehicle.currencyCode,
                passengerCapacity = b.vehicle.passengerCapacity,
                lastLocation = b.vehicle.lastLocation,
                routeProgress = b.vehicle.routeProgress,
                bearing = b.vehicle.bearing,
                lastServiceMileage = b.vehicle.lastServiceMileage,
                nextServiceMileage = b.vehicle.nextServiceMileage,
                routeNumber = b.routeNumber,
                doorCount = b.doorCount,
                standingCapacity = b.standingCapacity,
                hasAccessibilityRamp = b.hasAccessibilityRamp,
                hasAirConditioning = b.hasAirConditioning,
            )

        fun toVehicle(
            request: BusRequest,
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
                vehicleType = VehicleType.BUS,
                mileageKm = request.mileageKm,
                dailyRateAmount = request.dailyRate?.let { (it * 100).toInt() },
                passengerCapacity = request.passengerCapacity,
                lastServiceMileage = request.lastServiceMileage,
                nextServiceMileage = request.nextServiceMileage,
            )
    }
}
