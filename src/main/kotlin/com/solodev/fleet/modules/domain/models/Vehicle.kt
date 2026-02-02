package com.solodev.fleet.modules.domain.models

/** Value object representing a unique vehicle identifier. */
@JvmInline
value class VehicleId(val value: String) {
    init {
        require(value.isNotBlank()) { "Vehicle ID cannot be blank" }
    }
}

/** Vehicle status in the fleet lifecycle. */
enum class VehicleStatus {
    ACTIVE,
    RENTED,
    UNDER_MAINTENANCE,
    DECOMMISSIONED
}

/**
 * Vehicle domain entity.
 *
 * Represents a vehicle in the fleet management system. This is a pure domain model with no
 * framework dependencies.
 */
data class Vehicle(
        val id: VehicleId,
        val plateNumber: String,
        val make: String,
        val model: String,
        val year: Int,
        val status: VehicleStatus = VehicleStatus.ACTIVE,
        val passengerCapacity: Int? = null,
        val currentOdometerKm: Int = 0
) {
    init {
        require(plateNumber.isNotBlank()) { "Plate number cannot be blank" }
        require(make.isNotBlank()) { "Make cannot be blank" }
        require(model.isNotBlank()) { "Model cannot be blank" }
        require(year in 1900..2100) { "Year must be between 1900 and 2100" }
        require(currentOdometerKm >= 0) { "Odometer reading cannot be negative" }
        passengerCapacity?.let { require(it > 0) { "Passenger capacity must be positive" } }
    }

    /** Transition vehicle to rented status. Business rule: Can only rent active vehicles. */
    fun rent(): Vehicle {
        require(status == VehicleStatus.ACTIVE) { "Cannot rent vehicle in $status status" }
        return copy(status = VehicleStatus.RENTED)
    }

    /** Return vehicle to active status. Business rule: Can only return rented vehicles. */
    fun returnFromRental(): Vehicle {
        require(status == VehicleStatus.RENTED) { "Cannot return vehicle not in RENTED status" }
        return copy(status = VehicleStatus.ACTIVE)
    }

    /** Send vehicle for maintenance. Business rule: Cannot send rented vehicles to maintenance. */
    fun sendToMaintenance(): Vehicle {
        require(status != VehicleStatus.RENTED) { "Cannot send rented vehicle to maintenance" }
        return copy(status = VehicleStatus.UNDER_MAINTENANCE)
    }

    /** Complete maintenance and return to active. */
    fun completeMaintenance(): Vehicle {
        require(status == VehicleStatus.UNDER_MAINTENANCE) { "Vehicle is not under maintenance" }
        return copy(status = VehicleStatus.ACTIVE)
    }

    /** Decommission vehicle (terminal state). */
    fun decommission(): Vehicle = copy(status = VehicleStatus.DECOMMISSIONED)

    /** Update odometer reading. Business rule: Odometer can only increase. */
    fun updateOdometer(newReading: Int): Vehicle {
        require(newReading >= currentOdometerKm) {
            "Odometer reading cannot decrease (current: $currentOdometerKm, new: $newReading)"
        }
        return copy(currentOdometerKm = newReading)
    }
}
