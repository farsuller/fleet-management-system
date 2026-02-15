package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.accounts.application.AccountingService
import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.vehicles.domain.repository.VehicleRepository
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleState
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.Instant

class ActivateRentalUseCase(
        private val rentalRepository: RentalRepository,
        private val vehicleRepository: VehicleRepository,
        private val accountingService: AccountingService
) {
    suspend fun execute(id: String): Rental = newSuspendedTransaction(Dispatchers.IO) {
        val rental = rentalRepository.findById(RentalId(id)) ?: throw IllegalArgumentException("Rental not found")

        require(rental.status == RentalStatus.RESERVED) { "Can only activate reserved rentals" }

        // Get vehicle to capture current mileage
        val vehicle = vehicleRepository.findById(rental.vehicleId) ?: throw IllegalStateException("Vehicle not found")

        val activated = rental.activate(
            actualStart = Instant.now(),
            startOdo = vehicle.mileageKm // Capture current vehicle mileage
        )

        // Update vehicle state
        vehicleRepository.save(vehicle.copy(state = VehicleState.RENTED))

        val saved = rentalRepository.save(activated)

        accountingService.postRentalActivation(saved) // If this fails, the whole transaction rolls back

        saved
    }
}
