package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import java.time.Instant

class ActivateRentalUseCase(
        private val rentalRepository: RentalRepository,
        private val vehicleRepository: VehicleRepository
) {
    suspend fun execute(id: String): Rental {
        val rental =
                rentalRepository.findById(RentalId(id))
                        ?: throw IllegalArgumentException("Rental not found")

        require(rental.status == RentalStatus.RESERVED) { "Can only activate reserved rentals" }

        // Get vehicle to capture current mileage
        val vehicle =
                vehicleRepository.findById(rental.vehicleId)
                        ?: throw IllegalStateException("Vehicle not found")

        val activated =
                rental.activate(
                        actualStart = Instant.now(),
                        startOdo = vehicle.mileageKm // Capture current vehicle mileage
                )

        // Update vehicle state
        vehicleRepository.save(vehicle.copy(state = VehicleState.RENTED))

        return rentalRepository.save(activated)
    }
}
