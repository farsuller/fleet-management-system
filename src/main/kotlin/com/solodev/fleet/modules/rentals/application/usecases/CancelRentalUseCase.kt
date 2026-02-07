package com.solodev.fleet.modules.rentals.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.RentalRepository

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
