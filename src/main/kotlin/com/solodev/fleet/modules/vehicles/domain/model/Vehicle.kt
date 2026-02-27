package com.solodev.fleet.modules.vehicles.domain.model

import com.solodev.fleet.shared.domain.model.Location
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class VehicleId(val value: String) {
    init {
        require(value.isNotBlank()) { "Vehicle ID cannot be blank" }
    }
}

/** Vehicle state in the fleet lifecycle. */
@Serializable
enum class VehicleState {
    AVAILABLE,
    RENTED,
    MAINTENANCE,
    RETIRED
}

/**
 * Vehicle domain entity.
 *
 * Represents a vehicle in the fleet management system. This is a pure domain model with no
 * framework dependencies.
 */
@Serializable
data class Vehicle(
        val id: VehicleId,
        val vin: String,
        val licensePlate: String,
        val make: String,
        val model: String,
        val year: Int,
        val color: String? = null,
        val state: VehicleState = VehicleState.AVAILABLE,
        val mileageKm: Int = 0,
        val dailyRateAmount: Int? = null,
        val currencyCode: String = "PHP",
        val passengerCapacity: Int? = null,
        val lastLocation: Location? = null,
        val routeProgress: Double = 0.0,
        val bearing: Double = 0.0,
        val version: Long = 0
) {
    init {
        require(vin.isNotBlank()) { "VIN cannot be blank" }
        require(vin.length == 17) { "VIN must be exactly 17 characters" }
        require(licensePlate.isNotBlank()) { "License plate cannot be blank" }
        require(make.isNotBlank()) { "Make cannot be blank" }
        require(model.isNotBlank()) { "Model cannot be blank" }
        require(year in 1900..2100) { "Year must be between 1900 and 2100" }
        require(mileageKm >= 0) { "Mileage cannot be negative" }
        dailyRateAmount?.let { require(it >= 0) { "Daily rate cannot be negative" } }
        passengerCapacity?.let { require(it > 0) { "Passenger capacity must be positive" } }
        require(currencyCode == "PHP") { "Only PHP currency is supported" }
    }

    /** Transition vehicle to rented state. Business rule: Can only rent available vehicles. */
    fun rent(): Vehicle {
        require(state == VehicleState.AVAILABLE) { "Cannot rent vehicle in $state state" }
        return copy(state = VehicleState.RENTED)
    }

    /** Return vehicle to available state. Business rule: Can only return rented vehicles. */
    fun returnFromRental(): Vehicle {
        require(state == VehicleState.RENTED) { "Cannot return vehicle not in RENTED state" }
        return copy(state = VehicleState.AVAILABLE)
    }

    /** Send vehicle for maintenance. Business rule: Cannot send rented vehicles to maintenance. */
    fun sendToMaintenance(): Vehicle {
        require(state != VehicleState.RENTED) { "Cannot send rented vehicle to maintenance" }
        return copy(state = VehicleState.MAINTENANCE)
    }

    /** Complete maintenance and return to available. */
    fun completeMaintenance(): Vehicle {
        require(state == VehicleState.MAINTENANCE) { "Vehicle is not under maintenance" }
        return copy(state = VehicleState.AVAILABLE)
    }

    /** Retire vehicle (terminal state). */
    fun retire(): Vehicle = copy(state = VehicleState.RETIRED)

    /** Update mileage reading. Business rule: Mileage can only increase. */
    fun updateMileage(newMileage: Int): Vehicle {
        require(newMileage >= mileageKm) {
            "Mileage cannot decrease (current: $mileageKm, new: $newMileage)"
        }
        return copy(mileageKm = newMileage)
    }
}
