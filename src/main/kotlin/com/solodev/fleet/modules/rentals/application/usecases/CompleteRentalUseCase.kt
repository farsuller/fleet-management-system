package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.RentalRepository
import com.solodev.fleet.modules.domain.ports.VehicleRepository
import java.time.Instant

class CompleteRentalUseCase(
        private val rentalRepository: RentalRepository,
        private val vehicleRepository: VehicleRepository
) {
    suspend fun execute(id: String, finalMileage: Int? = null): Rental {
        val rental =
                rentalRepository.findById(RentalId(id))
                        ?: throw IllegalArgumentException("Rental not found")

        require(rental.status == RentalStatus.ACTIVE) { "Can only complete active rentals" }

        // Get vehicle to sync mileage
        val vehicle =
                vehicleRepository.findById(rental.vehicleId)
                        ?: throw IllegalStateException("Vehicle not found")

        // Use provided mileage or fallback to current vehicle mileage
        val actualEndOdo = finalMileage ?: vehicle.mileageKm

        // Business Rule: End mileage cannot be less than start mileage
        rental.startOdometerKm?.let { start ->
            require(actualEndOdo >= start) {
                "End mileage ($actualEndOdo) cannot be less than start mileage ($start)"
            }
        }

        val completed = rental.complete(actualEnd = Instant.now(), endOdo = actualEndOdo)

        // Update vehicle state and mileage
        val updatedVehicle = vehicle.copy(state = VehicleState.AVAILABLE, mileageKm = actualEndOdo)
        vehicleRepository.save(updatedVehicle)

        return rentalRepository.save(completed)
    }
}
