package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.Rental
import com.solodev.fleet.modules.domain.models.RentalId
import com.solodev.fleet.modules.domain.ports.RentalRepository
import java.time.Instant

class CompleteRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(rentalId: String, endOdometer: Int): Rental {
        val rental =
                repository.findById(RentalId(rentalId))
                        ?: throw IllegalArgumentException("Rental not found")

        val completedRental = rental.complete(Instant.now(), endOdometer)
        return repository.save(completedRental)
    }
}
