package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.rentals.domain.repository.RentalRepository
import com.solodev.fleet.modules.rentals.domain.model.Rental
import com.solodev.fleet.modules.rentals.domain.model.RentalId
import com.solodev.fleet.modules.rentals.domain.model.RentalStatus

class CancelRentalUseCase(private val repository: RentalRepository) {
    suspend fun execute(id: String): Rental {
        val rental =
                repository.findById(RentalId(id))
                        ?: throw IllegalArgumentException("Rental not found")

        require(rental.status in listOf(RentalStatus.RESERVED, RentalStatus.ACTIVE)) {
            "Can only cancel reserved or active rentals"
        }

        val cancelled = rental.cancel()
        return repository.save(cancelled)
    }
}
