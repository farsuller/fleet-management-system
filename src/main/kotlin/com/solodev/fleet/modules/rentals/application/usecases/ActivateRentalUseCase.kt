package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Rental
import com.solodev.fleet.modules.domain.models.RentalId
import com.solodev.fleet.modules.domain.ports.RentalRepository
import java.time.Instant

class ActivateRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(rentalId: String, startOdometer: Int): Rental {
        val rental =
                repository.findById(RentalId(rentalId))
                        ?: throw IllegalArgumentException("Rental not found")

        val activatedRental = rental.activate(Instant.now(), startOdometer)
        return repository.save(activatedRental)
    }
}
